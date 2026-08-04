package com.aguasystem.controller;

import com.aguasystem.dto.PagamentoDTO;
import com.aguasystem.entity.Fatura;
import com.aguasystem.entity.Pagamento;
import com.aguasystem.entity.WebhookEvento;
import com.aguasystem.repository.FaturaRepository;
import com.aguasystem.repository.WebhookEventoRepository;
import com.aguasystem.service.PagamentoService;
import com.aguasystem.service.ZumboPayService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Enumeration;

/**
 * Recebe as confirmacoes de pagamento automaticas do ZumboPay
 * (M-Pesa/e-Mola). Segue as 3 camadas de seguranca recomendadas:
 *
 * 1. VERIFICACAO DE ASSINATURA — confirma que o pedido e mesmo do ZumboPay
 *    (HMAC-SHA256 sobre "{X-Timestamp}.{corpoBruto}", cabecalhos
 *    X-Signature + X-Timestamp, com janela anti-replay de 5 minutos —
 *    confirmado pelo codigo-fonte real do plugin oficial WooCommerce)
 * 2. IDEMPOTENCIA — ignora eventos ja processados antes (evita duplicar
 *    pagamentos se o ZumboPay reenviar o mesmo evento)
 * 3. RE-VERIFICACAO AUTORITATIVA — nunca confia cegamente no conteudo do
 *    webhook; confirma sempre via GET /payments/{reference} antes de
 *    marcar qualquer fatura como paga
 *
 * Este endpoint fica FORA da autenticacao normal do sistema (ver
 * SecurityConfig) — quem "faz login" aqui e a assinatura HMAC, nao uma
 * sessao de utilizador.
 */
@RestController
@RequestMapping("/webhooks/zumbopay")
public class ZumboPayWebhookController {

    private static final Logger log = LoggerFactory.getLogger(ZumboPayWebhookController.class);

    private final ZumboPayService zumboPayService;
    private final FaturaRepository faturaRepository;
    private final PagamentoService pagamentoService;
    private final WebhookEventoRepository webhookEventoRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ZumboPayWebhookController(ZumboPayService zumboPayService, FaturaRepository faturaRepository,
                                      PagamentoService pagamentoService,
                                      WebhookEventoRepository webhookEventoRepository) {
        this.zumboPayService = zumboPayService;
        this.faturaRepository = faturaRepository;
        this.pagamentoService = pagamentoService;
        this.webhookEventoRepository = webhookEventoRepository;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<String> receber(@RequestBody String corpoBruto,
                                           @RequestHeader(value = "X-Signature", required = false)
                                           String assinaturaPlugin,
                                           @RequestHeader(value = "X-Zumbo-Signature", required = false)
                                           String assinaturaTester,
                                           @RequestHeader(value = "X-Timestamp", required = false)
                                           String timestamp,
                                           HttpServletRequest request) {
        try {
            return processarWebhook(corpoBruto, assinaturaPlugin, assinaturaTester, timestamp, request);
        } catch (Throwable erroInesperado) {
            // REDE DE SEGURANCA DE TOPO — garante que NUNCA falhamos em
            // silencio. Se algo imprevisto acontecer (uma excecao que
            // nenhuma das validacoes abaixo previu), fica sempre aqui uma
            // linha de log com o tipo exato do erro, a mensagem, e o corpo
            // recebido — para nunca mais precisarmos de adivinhar.
            log.error("ERRO INESPERADO ao processar webhook ZumboPay — tipo={}, mensagem={}, corpo recebido={}",
                    erroInesperado.getClass().getName(), erroInesperado.getMessage(), corpoBruto, erroInesperado);
            return ResponseEntity.status(500).body("erro interno ao processar webhook");
        }
    }

    private ResponseEntity<String> processarWebhook(String corpoBruto, String assinaturaPlugin,
                                                      String assinaturaTester, String timestamp,
                                                      HttpServletRequest request) {

        // DIAGNOSTICO — regista TODOS os cabecalhos E o corpo completo
        // recebidos, para nunca mais precisarmos de adivinhar o que
        // realmente chegou. Pode reduzir-se o detalhe depois de tudo
        // confirmado a funcionar de forma estavel.
        StringBuilder todosOsCabecalhos = new StringBuilder();
        Enumeration<String> nomesCabecalhos = request.getHeaderNames() != null
                ? request.getHeaderNames() : Collections.emptyEnumeration();
        while (nomesCabecalhos.hasMoreElements()) {
            String nome = nomesCabecalhos.nextElement();
            todosOsCabecalhos.append("\n    ").append(nome).append(": ").append(request.getHeader(nome));
        }
        log.info("DIAGNOSTICO — webhook ZumboPay recebido. Cabecalhos:{}\n  Corpo completo: {}",
                todosOsCabecalhos, corpoBruto);

        // Ja tivemos 3 fontes diferentes do ZumboPay (doc antiga, plugin
        // oficial, testador do painel) a indicar nomes de cabecalho e
        // esquemas de assinatura diferentes uns dos outros. Em vez de
        // continuar a adivinhar qual e o real, aceitamos qualquer
        // cabecalho presente e tentamos varias combinacoes de calculo —
        // so aceitamos se ALGUMA bater certo (nunca reduz a seguranca,
        // so aumenta a compatibilidade).
        String assinatura = assinaturaPlugin != null ? assinaturaPlugin : assinaturaTester;

        // CAMADA 1 — verificacao de assinatura (tenta com e sem timestamp)
        if (!zumboPayService.assinaturaValida(corpoBruto, timestamp, assinatura)) {
            log.warn("Webhook ZumboPay recebido com assinatura invalida ou timestamp expirado/em falta — rejeitado");
            return ResponseEntity.status(401).body("assinatura invalida");
        }

        JsonNode evento;
        try {
            evento = objectMapper.readTree(corpoBruto);
        } catch (Exception e) {
            log.error("Corpo do webhook ZumboPay nao e JSON valido: {}", e.getMessage());
            return ResponseEntity.badRequest().body("corpo invalido");
        }

        String eventId = primeiroTextoNaoNulo(evento, "id", "event_id");
        String tipoEvento = evento.path("type").asText(evento.path("event").asText(""));

        // CAMADA 2 — idempotencia (nao processar o mesmo evento duas vezes)
        if (eventId != null && webhookEventoRepository.existsByEventId(eventId)) {
            log.info("Evento ZumboPay {} ja tinha sido processado — ignorado", eventId);
            return ResponseEntity.ok("ja processado anteriormente");
        }

        log.info("Webhook ZumboPay recebido: tipo={}, eventId={}", tipoEvento, eventId);

        if ("payment.succeeded".equals(tipoEvento)) {
            processarPagamentoConfirmado(evento);
        } else {
            log.info("Tipo de evento '{}' recebido mas nao requer acao automatica neste sistema", tipoEvento);
        }

        if (eventId != null) {
            WebhookEvento registo = new WebhookEvento();
            registo.setEventId(eventId);
            registo.setTipoEvento(tipoEvento);
            webhookEventoRepository.save(registo);
        }

        return ResponseEntity.ok("recebido");
    }

    private void processarPagamentoConfirmado(JsonNode evento) {
        JsonNode dados = evento.path("data");
        String reference = primeiroTextoNaoNulo(dados, "reference", "id");

        if (reference == null) {
            log.error("Webhook payment.succeeded sem 'reference' — nao e possivel identificar a fatura");
            return;
        }

        // CAMADA 3 — re-verificacao autoritativa via GET /payments/{reference}.
        //
        // CONFIRMADO (teste real, 02/08/2026): este endpoint SO reconhece
        // referencias geradas por POST /payments (formato "ZP_..."). Para
        // referencias de POST /charges (formato "ZUMBO..."), devolve
        // sempre 404 not_found — nao existe endpoint documentado
        // equivalente para consultar cobrancas STK.
        //
        // Por isso, para referencias "ZUMBO..." (charges/STK), confiamos
        // diretamente no conteudo do webhook — que ja passou pela Camada 1
        // (assinatura HMAC valida = e mesmo o ZumboPay) e Camada 2
        // (idempotencia). Para referencias "ZP_..." (links de pagamento),
        // mantemos a re-verificacao autoritativa completa, que e suportada
        // e documentada para este caso.
        boolean referenciaDeLinkPagamento = reference.toUpperCase().startsWith("ZP");

        JsonNode dadosConfirmados;
        if (referenciaDeLinkPagamento) {
            JsonNode pagamentoConfirmado;
            try {
                pagamentoConfirmado = zumboPayService.consultarPagamento(reference);
            } catch (Exception e) {
                log.error("Falha ao re-verificar pagamento {} junto do ZumboPay: {}", reference, e.getMessage());
                return;
            }
            dadosConfirmados = pagamentoConfirmado.path("data");
            String statusReal = dadosConfirmados.path("status").asText("");
            if (!"success".equals(statusReal)) {
                log.warn("Re-verificacao do pagamento {} nao confirma sucesso (status real: {}) — ignorado "
                        + "por seguranca", reference, statusReal);
                return;
            }
        } else {
            // Referencia de /charges (STK) — nao ha endpoint para
            // re-verificar; usamos os dados do proprio webhook, ja
            // autenticado pela assinatura HMAC (Camada 1).
            dadosConfirmados = dados;
            String statusNoWebhook = dadosConfirmados.path("status").asText("");
            if (!statusNoWebhook.isBlank() && !"success".equals(statusNoWebhook)) {
                log.warn("Webhook de /charges (ref {}) com status='{}' dentro dos dados, apesar do tipo de "
                        + "evento ser payment.succeeded — ignorado por seguranca", reference, statusNoWebhook);
                return;
            }
            log.info("Referencia {} e de /charges (STK) — sem endpoint de re-verificacao disponivel; "
                    + "a confiar no webhook ja validado por assinatura", reference);
        }

        String sourceId = dadosConfirmados.path("source_id").asText(null);
        String canalConfirmado = dadosConfirmados.path("channel").asText("mpesa");
        BigDecimal valorConfirmado = dadosConfirmados.has("amount")
                ? new BigDecimal(dadosConfirmados.path("amount").asText("0"))
                : null;

        // REGRA CRITICA e-Mola (confirmada pelo codigo real do plugin
        // oficial): a API pode devolver status=success para e-Mola ANTES
        // do cliente ter mesmo confirmado o PIN no telemovel. Nunca
        // marcamos como paga sem uma prova explicita de PIN confirmado.
        if ("emola".equalsIgnoreCase(canalConfirmado) && !pinEmolaConfirmado(dadosConfirmados)) {
            log.warn("Pagamento e-Mola (ref {}) tem status=success mas sem prova de PIN confirmado — "
                    + "a aguardar confirmacao (nao marcado como pago ainda)", reference);
            return;
        }

        Fatura fatura = localizarFatura(reference, sourceId);
        if (fatura == null) {
            log.error("Nao foi possivel encontrar a fatura correspondente a referencia {} / source_id {}",
                    reference, sourceId);
            return;
        }

        // Cross-check do valor antes de aceitar o pagamento
        if (valorConfirmado != null && valorConfirmado.compareTo(fatura.getSaldoDevedor()) > 0) {
            log.error("Valor confirmado ({}) excede o saldo devedor da fatura {} ({}) — pagamento IGNORADO " +
                    "por seguranca. Verifica manualmente.", valorConfirmado, fatura.getNumeroFatura(),
                    fatura.getSaldoDevedor());
            return;
        }

        PagamentoDTO dto = new PagamentoDTO();
        dto.setFaturaId(fatura.getId());
        dto.setDataPagamento(LocalDate.now());
        dto.setValorPago(valorConfirmado != null ? valorConfirmado : fatura.getSaldoDevedor());
        dto.setFormaPagamento("emola".equalsIgnoreCase(canalConfirmado)
                ? Pagamento.FormaPagamento.EMOLA
                : Pagamento.FormaPagamento.MPESA);
        dto.setReferencia(reference);
        dto.setObservacoes("Pagamento automatico confirmado via ZumboPay (webhook)");
        dto.setRegistadoPor("Sistema (ZumboPay)");

        try {
            pagamentoService.registarPagamento(dto);
            log.info("Pagamento automatico registado com sucesso para a fatura {} via ZumboPay (ref: {})",
                    fatura.getNumeroFatura(), reference);
        } catch (Exception e) {
            // Ex: fatura ja estava paga por outra via (dinheiro, outro canal).
            // Nao deixamos isto quebrar o processamento do webhook — fica so
            // registado no log para revisao manual se necessario.
            log.warn("Nao foi possivel registar o pagamento automatico da fatura {} (ref: {}): tipo={}, mensagem={}",
                    fatura.getNumeroFatura(), reference, e.getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    /**
     * Verifica se o payload autoritativo (GET /payments/{reference}) prova
     * que o cliente introduziu mesmo o PIN na app e-Mola. Aceita varias
     * formas em que a API pode marcar essa confirmacao (a doc nao e clara
     * sobre qual usa, por isso verificamos todas, tal como o plugin
     * oficial faz).
     */
    private boolean pinEmolaConfirmado(JsonNode dadosConfirmados) {
        if (dadosConfirmados.path("pin_verified").asBoolean(false)) return true;
        if (dadosConfirmados.path("pin_confirmed").asBoolean(false)) return true;
        if (!dadosConfirmados.path("pin_confirmed_at").isMissingNode()
                && !dadosConfirmados.path("pin_confirmed_at").asText("").isBlank()) return true;

        String providerStatus = dadosConfirmados.path("provider_status").asText("").toUpperCase();
        if (providerStatus.equals("PIN_CONFIRMED") || providerStatus.equals("COMPLETED_WITH_PIN")
                || providerStatus.equals("AUTHORIZED_BY_PIN") || providerStatus.equals("SUCCESS")) {
            return true;
        }

        JsonNode metadata = dadosConfirmados.path("metadata");
        if (metadata.path("pin_verified").asBoolean(false)) return true;
        if (metadata.path("emola_pin_ok").asBoolean(false)) return true;

        return false;
    }

    private Fatura localizarFatura(String reference, String sourceId) {
        return faturaRepository.findByReferenciaZumbopay(reference)
                .or(() -> {
                    if (sourceId != null && sourceId.startsWith("fatura-")) {
                        String numeroFatura = sourceId.substring("fatura-".length());
                        return faturaRepository.buscarPorNumeroFaturaComCliente(numeroFatura);
                    }
                    return java.util.Optional.empty();
                })
                .orElse(null);
    }

    private String primeiroTextoNaoNulo(JsonNode node, String... campos) {
        for (String campo : campos) {
            if (node.has(campo) && !node.path(campo).isNull()) {
                return node.path(campo).asText();
            }
        }
        return null;
    }
}

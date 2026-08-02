package com.aguasystem.controller;

import com.aguasystem.dto.PagamentoDTO;
import com.aguasystem.entity.Fatura;
import com.aguasystem.entity.Pagamento;
import com.aguasystem.entity.WebhookEvento;
import com.aguasystem.repository.FaturaRepository;
import com.aguasystem.repository.WebhookEventoRepository;
import com.aguasystem.service.PagamentoService;
import com.aguasystem.service.ZumboPayService;
import com.aguasystem.service.ZumboPayReferenciaNaoEncontradaException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Recebe as confirmacoes de pagamento automaticas do ZumboPay
 * (M-Pesa/e-Mola). Segue as 3 camadas de seguranca recomendadas:
 *
 * 1. VERIFICACAO DE ASSINATURA — confirma que o pedido e mesmo do ZumboPay
 *    (HMAC-SHA256 sobre "{X-Timestamp}.{corpoBruto}", cabecalho
 *    X-Signature — ver CABECALHOS_ASSINATURA para as variantes aceites —,
 *    com janela anti-replay de 5 minutos)
 * 2. IDEMPOTENCIA — ignora eventos ja processados antes (evita duplicar
 *    pagamentos se o ZumboPay reenviar o mesmo evento)
 * 3. RE-VERIFICACAO AUTORITATIVA — nunca confia cegamente no conteudo do
 *    webhook; confirma sempre via GET /payments/{reference} antes de
 *    marcar qualquer fatura como paga
 * 4. GUARD DE PIN (e-Mola) — para pagamentos e-Mola, so aceita quando ha
 *    prova explicita de PIN confirmado no payload autoritativo
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

    /**
     * Nomes de cabecalho aceites para a assinatura HMAC, por ordem de
     * prioridade. O ZumboPay envia "X-Signature" na pratica (confirmado no
     * codigo-fonte do plugin oficial WooCommerce deles), mas a
     * documentacao escrita menciona "X-Zumbopay-Signature" — por isso
     * aceitamos varias variantes em vez de confiar cegamente num so nome.
     */
    private static final String[] CABECALHOS_ASSINATURA = {
            "X-Signature", "X-Zumbo-Signature", "X-Zumbopay-Signature", "X-ZumboPay-Signature"
    };

    // A doc so confirma "success" na resposta sincrona de /charges; aceitamos
    // tambem variantes plausiveis para GET /payments/{reference}, caso a API
    // devolva um nome de status ligeiramente diferente.
    private static final java.util.Set<String> STATUS_SUCESSO =
            java.util.Set.of("success", "succeeded", "paid", "completed");

    @PostMapping
    @Transactional
    public ResponseEntity<String> receber(@RequestBody String corpoBruto, HttpServletRequest requestHttp) {

        String assinatura = primeiroCabecalhoPresente(requestHttp, CABECALHOS_ASSINATURA);
        String timestamp = requestHttp.getHeader("X-Timestamp");

        // CAMADA 1 — verificacao de assinatura (inclui janela anti-replay)
        if (!zumboPayService.assinaturaValida(corpoBruto, timestamp, assinatura)) {
            log.warn("Webhook ZumboPay recebido com assinatura invalida, timestamp em falta/expirado — rejeitado");
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

        // CAMADA 3 — re-verificacao autoritativa: nunca confiar so no
        // conteudo do webhook, confirmar sempre com uma chamada GET direta.
        //
        // EXCECAO CONHECIDA: referencias geradas por POST /charges (STK
        // push, formato "ZUMBO...") nao existem no espaco de GET
        // /payments/{reference} segundo a documentacao do ZumboPay (esse
        // endpoint so e documentado para referencias de links de pagamento,
        // POST /payments, formato "ZP_..."). Quando isso acontece (404),
        // caimos para confiar no proprio payload do webhook — que ja passou
        // pela Camada 1 (assinatura HMAC) e Camada 2 (idempotencia).
        JsonNode dadosConfirmados;
        boolean viaFallbackWebhook = false;
        try {
            JsonNode pagamentoConfirmado = zumboPayService.consultarPagamento(reference);
            dadosConfirmados = pagamentoConfirmado.path("data");
        } catch (ZumboPayReferenciaNaoEncontradaException e) {
            if (reference.startsWith("ZUMBO")) {
                log.warn("Referencia {} nao encontrada em GET /payments (esperado para referencias de /charges) " +
                        "— a confiar no payload do webhook, ja validado por assinatura HMAC.", reference);
                dadosConfirmados = dados;
                viaFallbackWebhook = true;
            } else {
                log.error("Referencia {} nao encontrada no ZumboPay — pagamento IGNORADO.", reference);
                return;
            }
        } catch (Exception e) {
            log.error("Falha ao re-verificar pagamento {} junto do ZumboPay: {}", reference, e.getMessage());
            return;
        }

        String statusReal = dadosConfirmados.path("status").asText("");
        if (!viaFallbackWebhook && !STATUS_SUCESSO.contains(statusReal.toLowerCase())) {
            log.warn("Re-verificacao do pagamento {} nao confirma sucesso (status real: {}) — ignorado por seguranca",
                    reference, statusReal);
            return;
        }

        String sourceId = dadosConfirmados.path("source_id").asText(null);
        String canalConfirmado = dadosConfirmados.path("channel").asText("mpesa");
        BigDecimal valorConfirmado = dadosConfirmados.has("amount")
                ? new BigDecimal(dadosConfirmados.path("amount").asText("0"))
                : null;

        // e-Mola exige confirmacao de PIN antes de marcarmos como pago —
        // ver javadoc de pinEmolaConfirmado(). M-Pesa nao precisa desta
        // verificacao extra.
        if ("emola".equalsIgnoreCase(canalConfirmado) && !pinEmolaConfirmado(dadosConfirmados)) {
            log.info("Pagamento e-Mola {} com status=success mas sem prova de PIN confirmado ainda — " +
                    "tratado como pendente, a aguardar novo webhook/consulta.", reference);
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
            log.warn("Nao foi possivel registar o pagamento automatico da fatura {} (ref: {}): {}",
                    fatura.getNumeroFatura(), reference, e.getMessage());
        }
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

    private String primeiroCabecalhoPresente(HttpServletRequest request, String... nomes) {
        for (String nome : nomes) {
            String valor = request.getHeader(nome);
            if (valor != null && !valor.isBlank()) {
                return valor;
            }
        }
        return null;
    }

    /**
     * REGRA CRITICA para e-Mola (confirmada no verificador oficial do
     * ZumboPay): o e-Mola exige que o cliente introduza o PIN na app dele.
     * NUNCA marcar a fatura como paga so porque "status":"success" veio no
     * payload — e preciso confirmar tambem uma prova explicita de PIN
     * (pin_verified / pin_confirmed_at / provider_status conhecido).
     * Se a prova nao estiver presente, tratamos como pendente e deixamos
     * para o proximo webhook (ou nova consulta manual) confirmar depois.
     * Para M-Pesa esta prova nao e exigida pela API, por isso so se aplica
     * quando o canal confirmado e "emola".
     */
    private boolean pinEmolaConfirmado(JsonNode dadosConfirmados) {
        if (dadosConfirmados.path("pin_verified").asBoolean(false)) return true;
        if (dadosConfirmados.path("pin_confirmed").asBoolean(false)) return true;
        if (dadosConfirmados.hasNonNull("pin_confirmed_at")) return true;
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
}

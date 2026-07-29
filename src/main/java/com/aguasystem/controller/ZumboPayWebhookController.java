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

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Recebe as confirmacoes de pagamento automaticas do ZumboPay
 * (M-Pesa/e-Mola). Segue as 3 camadas de seguranca recomendadas:
 *
 * 1. VERIFICACAO DE ASSINATURA — confirma que o pedido e mesmo do ZumboPay
 *    (HMAC-SHA256 sobre "{X-Timestamp}.{corpoBruto}", cabecalho
 *    X-Zumbo-Signature, com janela anti-replay de 5 minutos)
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
                                           @RequestHeader(value = "X-Zumbo-Signature", required = false)
                                           String assinatura,
                                           @RequestHeader(value = "X-Timestamp", required = false)
                                           String timestamp) {

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
        // conteudo do webhook, confirmar sempre com uma chamada GET direta
        JsonNode pagamentoConfirmado;
        try {
            pagamentoConfirmado = zumboPayService.consultarPagamento(reference);
        } catch (Exception e) {
            log.error("Falha ao re-verificar pagamento {} junto do ZumboPay: {}", reference, e.getMessage());
            return;
        }

        JsonNode dadosConfirmados = pagamentoConfirmado.path("data");
        String statusReal = dadosConfirmados.path("status").asText("");
        if (!"success".equals(statusReal)) {
            log.warn("Re-verificacao do pagamento {} nao confirma sucesso (status real: {}) — ignorado por seguranca",
                    reference, statusReal);
            return;
        }

        String sourceId = dadosConfirmados.path("source_id").asText(null);
        String canalConfirmado = dadosConfirmados.path("channel").asText("mpesa");
        BigDecimal valorConfirmado = dadosConfirmados.has("amount")
                ? new BigDecimal(dadosConfirmados.path("amount").asText("0"))
                : null;

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
}

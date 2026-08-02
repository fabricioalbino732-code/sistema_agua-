package com.aguasystem.service;

import com.aguasystem.exception.NegocioException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Integra com a API do ZumboPay para cobrancas diretas via M-Pesa/e-Mola
 * (STK push — o cliente recebe o pedido de PIN diretamente no telemovel,
 * sem sair do sistema).
 *
 * Desativado por padrao (zumbopay.ativado=false) — enquanto assim for, o
 * sistema so regista no log o que faria, sem pedidos reais.
 */
@Service
public class ZumboPayService {

    private static final Logger log = LoggerFactory.getLogger(ZumboPayService.class);

    @Value("${zumbopay.api-key:}")
    private String apiKey;

    @Value("${zumbopay.merchant-id:}")
    private String merchantId;

    @Value("${zumbopay.wallet-id-mpesa:}")
    private String walletIdMpesa;

    @Value("${zumbopay.wallet-id-emola:}")
    private String walletIdEmola;

    @Value("${zumbopay.api.base-url:https://zumbopay.com/api/public/v1}")
    private String baseUrl;

    @Value("${zumbopay.ativado:false}")
    private boolean ativado;

    @Value("${zumbopay.webhook-secret:}")
    private String webhookSecret;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public enum Canal { MPESA, EMOLA }

    public static class ResultadoCobranca {
        public String status;      // "success", "pending", "declined", "erro"
        public String reference;   // referencia ZumboPay para consultas futuras
        public String mensagem;
    }

    public static class ResultadoLinkPagamento {
        public boolean sucesso;
        public String checkoutUrl;
        public String referencia;  // usado para o webhook conseguir encontrar a fatura depois
        public String mensagem;
    }

    /**
     * Gera um link de checkout hospedado (POST /payments) para uma fatura —
     * ao contrario de cobrar(), que dispara um STK push imediato e expira em
     * segundos, este link fica valido por mais tempo e pode ser enviado por
     * SMS para o cliente pagar quando quiser (M-Pesa, e-Mola ou cartao,
     * conforme as carteiras configuradas).
     *
     * Os canais oferecidos dependem de quais wallet_id estao configurados:
     * so oferecemos e-Mola se zumbopay.wallet-id-emola estiver preenchido.
     */
    public ResultadoLinkPagamento gerarLinkPagamento(BigDecimal valor, String numeroFatura) {
        ResultadoLinkPagamento resultado = new ResultadoLinkPagamento();

        if (!ativado) {
            resultado.sucesso = false;
            resultado.mensagem = "Integracao ZumboPay desativada — nenhum link foi gerado";
            log.info("[ZUMBOPAY DESATIVADO] Link de pagamento que seria gerado para fatura {} (valor {})",
                    numeroFatura, valor);
            return resultado;
        }

        if (apiKey.isBlank() || walletIdMpesa.isBlank()) {
            resultado.sucesso = false;
            resultado.mensagem = "Credenciais ou wallet_id_mpesa do ZumboPay nao configurados";
            log.warn(resultado.mensagem);
            return resultado;
        }

        try {
            java.util.List<String> canais = new java.util.ArrayList<>();
            canais.add("mpesa");
            if (walletIdEmola != null && !walletIdEmola.isBlank()) {
                canais.add("emola");
            }

            Map<String, Object> corpo = new LinkedHashMap<>();
            // Confirmado pelo codigo real do plugin oficial: o pedido de
            // /payments deve indicar explicitamente o tipo ("link" para um
            // link de pagamento simples, "recurring" para subscricoes,
            // "split" para multi-beneficiario). Sem isto, o ZumboPay pode
            // nao processar o source_id/reference correctamente para
            // reconciliacao (possivel causa do erro "Missing Reference").
            corpo.put("type", "link");
            corpo.put("title", "Fatura #" + numeroFatura);
            corpo.put("amount", valor);
            corpo.put("currency", "MZN");
            corpo.put("channels", canais);
            corpo.put("wallet_id", walletIdMpesa);
            // Confirmado pelo codigo real do plugin: /payments tambem
            // aceita e usa source_id para o webhook conseguir encontrar a
            // fatura depois (nao era so uma tentativa defensiva).
            corpo.put("source_id", "fatura-" + numeroFatura);

            String json = objectMapper.writeValueAsString(corpo);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/payments"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("X-Merchant-Id", merchantId)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode corpoResposta = objectMapper.readTree(response.body());

            if (response.statusCode() == 200 || response.statusCode() == 201) {
                String checkoutUrl = corpoResposta.path("data").path("checkout_url").asText(null);
                if (checkoutUrl == null || checkoutUrl.isBlank()) {
                    resultado.sucesso = false;
                    resultado.mensagem = "ZumboPay respondeu sem checkout_url: " + response.body();
                    log.error(resultado.mensagem);
                    return resultado;
                }
                resultado.sucesso = true;
                resultado.checkoutUrl = checkoutUrl;
                // Tenta capturar uma referencia explicita da API (campos
                // comuns: "reference", "id", "payment_id"); se nao vier
                // nenhuma, usa o ultimo segmento do proprio checkout_url
                // (ex: ".../pay/zp-abc123" -> "zp-abc123") como fallback,
                // para o webhook ter pelo menos alguma pista de correlacao.
                String referencia = primeiroTextoNaoNulo(corpoResposta.path("data"), "reference", "id", "payment_id");
                if (referencia == null) {
                    String[] segmentos = checkoutUrl.split("/");
                    referencia = segmentos[segmentos.length - 1];
                }
                resultado.referencia = referencia;
                resultado.mensagem = "Link de pagamento gerado com sucesso";
                log.info("Link de pagamento ZumboPay gerado para fatura {}: {} (referencia: {})",
                        numeroFatura, checkoutUrl, referencia);
            } else {
                resultado.sucesso = false;
                resultado.mensagem = corpoResposta.path("error").path("message").asText("Erro desconhecido");
                log.error("Falha ao gerar link de pagamento (status {}): {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            resultado.sucesso = false;
            resultado.mensagem = "Erro de comunicacao com o ZumboPay: " + e.getMessage();
            log.error("Erro ao gerar link de pagamento ZumboPay: {}", e.getMessage(), e);
        }

        return resultado;
    }

    /**
     * Inicia uma cobranca directa (STK push) — o cliente recebe o pedido de
     * PIN no telemovel dele. Usa 'source_id' com o numero da fatura para
     * garantir idempotencia (nao cobra duas vezes pela mesma fatura).
     */
    public ResultadoCobranca cobrar(Canal canal, BigDecimal valor, String msisdn,
                                     String nomeCliente, String numeroFatura) {
        ResultadoCobranca resultado = new ResultadoCobranca();
        String telefoneFormatado = formatarTelefoneMocambicano(msisdn);
        String sourceId = "fatura-" + numeroFatura;

        if (!ativado) {
            log.info("[ZUMBOPAY DESATIVADO] Cobranca que seria feita: canal={}, valor={}, telefone={}, fatura={}",
                    canal, valor, telefoneFormatado, numeroFatura);
            resultado.status = "desativado";
            resultado.mensagem = "Integracao ZumboPay desativada — nenhum pedido real foi feito";
            return resultado;
        }

        String walletId = canal == Canal.MPESA ? walletIdMpesa : walletIdEmola;
        if (apiKey.isBlank() || walletId.isBlank()) {
            resultado.status = "erro";
            resultado.mensagem = "Credenciais ou wallet_id do ZumboPay nao configurados";
            log.warn(resultado.mensagem);
            return resultado;
        }

        try {
            Map<String, Object> corpo = new LinkedHashMap<>();
            corpo.put("wallet_id", walletId);
            corpo.put("amount", valor);
            corpo.put("msisdn", telefoneFormatado);
            corpo.put("customer_name", nomeCliente);
            corpo.put("source_id", sourceId);

            String json = objectMapper.writeValueAsString(corpo);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/charges"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("X-Merchant-Id", merchantId)
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", sourceId)
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode corpoResposta = objectMapper.readTree(response.body());

            if (response.statusCode() == 200 || response.statusCode() == 202) {
                JsonNode data = corpoResposta.path("data");
                resultado.status = data.path("status").asText();
                resultado.reference = data.path("reference").asText();
                resultado.mensagem = response.statusCode() == 200
                        ? "Pagamento confirmado imediatamente"
                        : "Pedido enviado — a aguardar o cliente introduzir o PIN no telemovel";
                log.info("Cobranca ZumboPay iniciada para fatura {}: status={}, reference={}",
                        numeroFatura, resultado.status, resultado.reference);
            } else {
                resultado.status = "erro";
                resultado.mensagem = corpoResposta.path("error").path("message").asText("Erro desconhecido");
                log.error("Falha ao cobrar via ZumboPay (status {}): {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            resultado.status = "erro";
            resultado.mensagem = "Erro de comunicacao com o ZumboPay: " + e.getMessage();
            log.error("Erro ao cobrar via ZumboPay: {}", e.getMessage(), e);
        }

        return resultado;
    }

    /**
     * Consulta o estado atual de um pagamento pela referencia ZumboPay.
     * Usado para RE-VERIFICACAO AUTORITATIVA quando um webhook chega —
     * nunca confiamos cegamente no conteudo do webhook, confirmamos sempre
     * aqui antes de marcar uma fatura como paga.
     */
    public JsonNode consultarPagamento(String reference) {
        if (apiKey.isBlank()) {
            throw new NegocioException("Credenciais do ZumboPay nao configuradas");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/payments/" + reference))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("X-Merchant-Id", merchantId)
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return objectMapper.readTree(response.body());
        } catch (Exception e) {
            throw new NegocioException("Erro ao consultar pagamento no ZumboPay: " + e.getMessage());
        }
    }

    private String formatarTelefoneMocambicano(String telefone) {
        String limpo = telefone.replaceAll("[^0-9]", "");
        if (limpo.startsWith("258")) return limpo;
        if (limpo.startsWith("0")) limpo = limpo.substring(1);
        return "258" + limpo;
    }

    public boolean isAtivado() {
        return ativado;
    }

    /**
     * Janela maxima (em milissegundos) entre o momento em que o ZumboPay
     * assinou o webhook (X-Timestamp) e o momento em que o recebemos.
     * Protege contra replay de webhooks antigos capturados por terceiros.
     * (Confirmado pelo codigo real do plugin oficial: MAX_SKEW_MS = 300000)
     */
    private static final long JANELA_ANTI_REPLAY_MS = 300_000;

    /**
     * Verifica a assinatura HMAC-SHA256 do webhook ZumboPay.
     *
     * CONFIRMADO pelo codigo-fonte real do plugin oficial WooCommerce
     * (includes/class-webhook.php) — a fonte mais fiavel que tivemos ate
     * agora, mais do que qualquer resumo de documentacao:
     *
     *   Cabecalhos: X-Signature (hex) e X-Timestamp (epoch em MILISSEGUNDOS)
     *   Formula:    hex(hmac_sha256("{X-Timestamp}.{corpoBruto}", secret))
     *   Janela anti-replay: 5 minutos (300000 ms)
     *
     * (Uma tentativa anterior, baseada num resumo de documentacao
     * diferente, usava so o corpo sem timestamp — estava errada e
     * rejeitava todos os webhooks reais. Corrigido aqui com base no
     * codigo real, testado, do plugin.)
     */
    public boolean assinaturaValida(String corpoBruto, String timestamp, String assinaturaRecebida) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.error("Webhook secret do ZumboPay nao configurado — a rejeitar webhook por seguranca");
            return false;
        }
        if (assinaturaRecebida == null || assinaturaRecebida.isBlank()) {
            log.warn("Webhook ZumboPay sem cabecalho X-Signature — rejeitado");
            return false;
        }
        if (timestamp == null || timestamp.isBlank()) {
            log.warn("Webhook ZumboPay sem cabecalho X-Timestamp — rejeitado");
            return false;
        }
        // O X-Signature pode vir com prefixo "sha256=" (visto no plugin real)
        String assinaturaLimpa = assinaturaRecebida.replaceFirst("(?i)^sha256=", "").trim();

        long timestampMs;
        try {
            timestampMs = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException e) {
            log.warn("Webhook ZumboPay com X-Timestamp invalido: {}", timestamp);
            return false;
        }
        long agoraMs = System.currentTimeMillis();
        if (Math.abs(agoraMs - timestampMs) > JANELA_ANTI_REPLAY_MS) {
            log.warn("Webhook ZumboPay com X-Timestamp fora da janela anti-replay (timestamp={}) — rejeitado",
                    timestamp);
            return false;
        }

        try {
            String mensagemAssinada = timestamp + "." + corpoBruto;

            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec chave = new javax.crypto.spec.SecretKeySpec(
                    webhookSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(chave);
            byte[] hashCalculado = mac.doFinal(mensagemAssinada.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String assinaturaEsperada = bytesParaHex(hashCalculado);

            return java.security.MessageDigest.isEqual(
                    assinaturaEsperada.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    assinaturaLimpa.getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Erro ao verificar assinatura do webhook ZumboPay: {}", e.getMessage(), e);
            return false;
        }
    }

    private String bytesParaHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String primeiroTextoNaoNulo(JsonNode node, String... campos) {
        for (String campo : campos) {
            if (node.has(campo) && !node.path(campo).isNull()) {
                String valor = node.path(campo).asText();
                if (valor != null && !valor.isBlank()) {
                    return valor;
                }
            }
        }
        return null;
    }
}

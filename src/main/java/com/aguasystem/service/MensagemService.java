package com.aguasystem.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Envia SMS automaticos aos clientes (ex: aviso de fatura nova) atraves da
 * API do MozeSMS. Desativado por padrao (notificacoes.sms.ativado=false) —
 * ate teres um token real configurado, o sistema regista no log o que
 * TERIA enviado, mas nao faz nenhum pedido real, evitando erros por falta
 * de credenciais.
 *
 * Falhas no envio de SMS NUNCA interrompem o funcionamento normal do
 * sistema (ex: gerar uma fatura continua a funcionar mesmo que o SMS falhe)
 * — o erro fica so registado no log.
 */
@Service
public class MensagemService {

    private static final Logger log = LoggerFactory.getLogger(MensagemService.class);

    @Value("${mozesms.api-key:}")
    private String apiKey;

    @Value("${mozesms.api-secret:}")
    private String apiSecret;

    @Value("${mozesms.from:MozeSMS}")
    private String remetente;

    @Value("${mozesms.api.url:https://api.mozesms.com/sms/send}")
    private String apiUrl;

    @Value("${notificacoes.sms.ativado:false}")
    private boolean ativado;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public static class ResultadoSms {
        public boolean sucesso;
        public String mensagem;

        public static ResultadoSms ok() {
            ResultadoSms r = new ResultadoSms();
            r.sucesso = true;
            r.mensagem = "SMS enviado com sucesso";
            return r;
        }

        public static ResultadoSms falha(String motivo) {
            ResultadoSms r = new ResultadoSms();
            r.sucesso = false;
            r.mensagem = motivo;
            return r;
        }
    }

    public ResultadoSms enviarSms(String telefoneDestino, String mensagem) {
        if (telefoneDestino == null || telefoneDestino.isBlank()) {
            log.warn("Cliente sem numero de telefone registado — SMS nao enviado");
            return ResultadoSms.falha("Cliente sem numero de telefone registado");
        }

        String telefoneFormatado = formatarTelefoneMocambicano(telefoneDestino);

        if (!ativado) {
            log.info("[SMS DESATIVADO] Mensagem que seria enviada para {}: \"{}\"", telefoneFormatado, mensagem);
            return ResultadoSms.falha("Notificacoes por SMS estao desativadas (notificacoes.sms.ativado=false)");
        }

        if (apiKey == null || apiKey.isBlank() || apiSecret == null || apiSecret.isBlank()) {
            log.warn("Credenciais do MozeSMS nao configuradas (mozesms.api-key / mozesms.api-secret). " +
                    "SMS NAO enviado para {}", telefoneFormatado);
            return ResultadoSms.falha("Credenciais do MozeSMS nao configuradas");
        }

        try {
            Map<String, String> corpo = Map.of(
                    "phone", telefoneFormatado,
                    "message", mensagem,
                    "sender_id", remetente
            );
            String json = objectMapper.writeValueAsString(corpo);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("X-API-Key", apiKey)
                    .header("X-API-Secret", apiSecret)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            com.fasterxml.jackson.databind.JsonNode corpoResposta = objectMapper.readTree(response.body());

            // A MozeSMS devolve sempre um campo "success" no corpo, mesmo em
            // 200 — confirmamos isso, nao so o codigo HTTP, para nunca
            // assumirmos sucesso indevidamente.
            if (response.statusCode() == 200 && corpoResposta.path("success").asBoolean(false)) {
                String idMensagem = corpoResposta.path("data").path("id").asText("");
                log.info("SMS enviado com sucesso para {} (id={})", telefoneFormatado, idMensagem);
                return ResultadoSms.ok();
            } else {
                String motivo = corpoResposta.path("error").asText(null);
                if (motivo == null || motivo.isBlank()) {
                    motivo = "MozeSMS respondeu com erro (status " + response.statusCode() + "): " + response.body();
                }
                log.error("Falha ao enviar SMS para {} (status {}): {}",
                        telefoneFormatado, response.statusCode(), response.body());
                return ResultadoSms.falha(motivo);
            }
        } catch (Exception e) {
            // Nao interrompe o resto do sistema, mas agora o chamador SABE que falhou
            log.error("Erro ao enviar SMS para {}: {}", telefoneFormatado, e.getMessage(), e);
            return ResultadoSms.falha("Erro de comunicacao com o MozeSMS: " + e.getMessage());
        }
    }

    /**
     * Normaliza numeros moçambicanos para o formato internacional que a
     * API espera (ex: "258847001234"), aceitando entradas como
     * "84 700 1234", "0847001234" ou ja "258847001234".
     */
    private String formatarTelefoneMocambicano(String telefone) {
        String limpo = telefone.replaceAll("[^0-9]", "");

        if (limpo.startsWith("258")) {
            return limpo;
        }
        if (limpo.startsWith("0")) {
            limpo = limpo.substring(1);
        }
        return "258" + limpo;
    }
}

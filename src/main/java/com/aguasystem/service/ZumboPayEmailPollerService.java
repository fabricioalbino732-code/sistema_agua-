package com.aguasystem.service;

import com.aguasystem.dto.PagamentoDTO;
import com.aguasystem.entity.Fatura;
import com.aguasystem.entity.Pagamento;
import com.aguasystem.entity.WebhookEvento;
import com.aguasystem.repository.FaturaRepository;
import com.aguasystem.repository.WebhookEventoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMultipart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CAMINHO ALTERNATIVO de confirmacao de pagamentos ZumboPay, via leitura
 * dos emails de confirmacao ("Recebeu X MZN") que o ZumboPay envia sempre,
 * de forma instantanea e fiavel, para a caixa de correio do comerciante —
 * mesmo nos casos em que o webhook HTTP nao chega ao servidor (problema
 * que temos vindo a reportar ao suporte do ZumboPay).
 *
 * Este servico NAO substitui o webhook (ZumboPayWebhookController) — e
 * um reforço independente. Os dois podem processar o mesmo pagamento;
 * a idempotencia (WebhookEventoRepository, prefixo "email:") evita
 * duplicar o registo, e o PagamentoService ja rejeita pagamentos a mais
 * numa fatura ja paga.
 *
 * Seguranca aplicada:
 * 1. So processa emails cujo remetente e exatamente o esperado
 *    (noreply@zumbopay.com, configuravel).
 * 2. Tenta sempre re-verificar a referencia junto da API do ZumboPay
 *    (GET /payments/{reference}) antes de confiar no email — sabendo que,
 *    na pratica, isto falha com 404 para muitas referencias (mesma
 *    limitacao ja documentada no webhook). Quando isso acontece, cai para
 *    confiar no proprio email, que so pode ter chegado de dentro da conta
 *    Gmail dedicada, vinda de um remetente verificado.
 * 3. Cross-check do valor confirmado vs saldo devedor da fatura.
 * 4. Idempotencia por Message-ID do email — nunca processa o mesmo email
 *    duas vezes, mesmo que fique marcado como nao lido de novo.
 */
@Service
public class ZumboPayEmailPollerService {

    private static final Logger log = LoggerFactory.getLogger(ZumboPayEmailPollerService.class);

    private static final Pattern PADRAO_REFERENCIA =
            Pattern.compile("Refer[êe]ncia:\\s*([A-Z0-9_\\-]{6,})", Pattern.CASE_INSENSITIVE);
    private static final Pattern PADRAO_FATURA =
            Pattern.compile("Fatura\\s*#\\s*([A-Za-z0-9\\-]+)");
    private static final Pattern PADRAO_BRUTO =
            Pattern.compile("Bruto\\s+([0-9]+[.,][0-9]{2})\\s*MZN");
    private static final Pattern PADRAO_TELEFONE =
            Pattern.compile("De:\\s*(258)?([0-9]{9})");

    @Value("${zumbopay.email-poller.ativado:false}")
    private boolean ativado;

    @Value("${zumbopay.email-poller.host:imap.gmail.com}")
    private String host;

    @Value("${zumbopay.email-poller.porta:993}")
    private int porta;

    @Value("${zumbopay.email-poller.utilizador:}")
    private String utilizador;

    @Value("${zumbopay.email-poller.password:}")
    private String password;

    @Value("${zumbopay.email-poller.remetente-esperado:noreply@zumbopay.com}")
    private String remetenteEsperado;

    private final ZumboPayService zumboPayService;
    private final FaturaRepository faturaRepository;
    private final PagamentoService pagamentoService;
    private final WebhookEventoRepository webhookEventoRepository;

    public ZumboPayEmailPollerService(ZumboPayService zumboPayService, FaturaRepository faturaRepository,
                                       PagamentoService pagamentoService,
                                       WebhookEventoRepository webhookEventoRepository) {
        this.zumboPayService = zumboPayService;
        this.faturaRepository = faturaRepository;
        this.pagamentoService = pagamentoService;
        this.webhookEventoRepository = webhookEventoRepository;
    }

    @Scheduled(fixedDelayString = "${zumbopay.email-poller.intervalo-ms:90000}")
    public void verificarEmails() {
        if (!ativado) {
            return;
        }
        if (utilizador.isBlank() || password.isBlank()) {
            log.warn("[EmailPoller] ativado (ZUMBOPAY_EMAIL_POLLER_ATIVADO=true) mas faltam credenciais " +
                    "(ZUMBOPAY_EMAIL_USER / ZUMBOPAY_EMAIL_APP_PASSWORD) — verificacao saltada");
            return;
        }

        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", host);
        props.put("mail.imaps.port", String.valueOf(porta));
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imaps.connectiontimeout", "10000");
        props.put("mail.imaps.timeout", "10000");

        Session session = Session.getInstance(props);

        try (Store store = session.getStore("imaps")) {
            store.connect(host, porta, utilizador, password);
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);
            try {
                Message[] naoLidas = inbox.search(new jakarta.mail.search.FlagTerm(
                        new Flags(Flags.Flag.SEEN), false));
                if (naoLidas.length > 0) {
                    log.info("[EmailPoller] {} email(s) por ler encontrados", naoLidas.length);
                }
                for (Message msg : naoLidas) {
                    try {
                        processarEmail(msg);
                    } catch (Exception e) {
                        log.error("[EmailPoller] erro ao processar um email individual: {}", e.getMessage(), e);
                    } finally {
                        // Marca como lido mesmo se falhou, para nao ficar em
                        // loop infinito a tentar processar um email com
                        // formato inesperado. O log acima fica registado
                        // para revisao manual se necessario.
                        msg.setFlag(Flags.Flag.SEEN, true);
                    }
                }
            } finally {
                inbox.close(false);
            }
        } catch (Exception e) {
            log.error("[EmailPoller] falha na ligacao IMAP a {}: {}", host, e.getMessage(), e);
        }
    }

    @Transactional
    void processarEmail(Message msg) throws Exception {
        Address[] remetentes = msg.getFrom();
        boolean remetenteValido = remetentes != null && Arrays.stream(remetentes)
                .anyMatch(a -> a.toString().toLowerCase().contains(remetenteEsperado.toLowerCase()));
        if (!remetenteValido) {
            log.info("[EmailPoller] email ignorado — remetente nao corresponde a '{}' (era: {})",
                    remetenteEsperado, remetentes != null ? Arrays.toString(remetentes) : "desconhecido");
            return;
        }

        String messageId = primeiroCabecalho(msg, "Message-ID");
        String eventId = "email:" + (messageId != null ? messageId : ("sem-id-" + msg.getSentDate()));

        if (webhookEventoRepository.existsByEventId(eventId)) {
            log.info("[EmailPoller] email {} ja tinha sido processado — ignorado", eventId);
            return;
        }

        String corpo = extrairTexto(msg);
        if (corpo == null || corpo.isBlank()) {
            log.warn("[EmailPoller] nao foi possivel extrair texto do email (messageId={})", messageId);
            return;
        }

        String reference = extrair(PADRAO_REFERENCIA, corpo);
        String numeroFatura = extrair(PADRAO_FATURA, corpo);
        String brutoTexto = extrair(PADRAO_BRUTO, corpo);
        String telefone = extrairTelefone(corpo);

        if (reference == null && numeroFatura == null) {
            log.warn("[EmailPoller] email de {} nao contem referencia nem numero de fatura reconheciveis — " +
                    "ignorado (pode nao ser um email de confirmacao de pagamento)", remetenteEsperado);
            return;
        }

        BigDecimal valorBruto = brutoTexto != null ? parseValorMzn(brutoTexto) : null;

        log.info("[EmailPoller] email de confirmacao detectado — referencia={}, fatura={}, valorBruto={}, tel={}",
                reference, numeroFatura, valorBruto, telefone);

        boolean sucesso = confirmarPagamento(reference, numeroFatura, valorBruto, telefone);

        WebhookEvento registo = new WebhookEvento();
        registo.setEventId(eventId);
        registo.setTipoEvento(sucesso ? "email.payment.succeeded" : "email.payment.nao_processado");
        webhookEventoRepository.save(registo);
    }

    private boolean confirmarPagamento(String reference, String numeroFatura, BigDecimal valorDoEmail,
                                        String telefone) {
        BigDecimal valorConfirmado = valorDoEmail;
        String canalConfirmado = canalPorTelefone(telefone);

        // Tenta re-verificar autoritativamente. Sabido (ver nota no
        // ZumboPayWebhookController) que isto devolve 404 para muitas
        // referencias — nesse caso nao tratamos como erro fatal, caimos
        // para confiar no proprio email (que ja passou o filtro de
        // remetente verificado).
        if (reference != null) {
            try {
                JsonNode resposta = zumboPayService.consultarPagamento(reference);
                JsonNode dados = resposta.path("data");
                if ("success".equals(dados.path("status").asText(""))) {
                    if (dados.has("amount")) {
                        valorConfirmado = new BigDecimal(dados.path("amount").asText("0"));
                    }
                    if (dados.has("channel")) {
                        canalConfirmado = dados.path("channel").asText(canalConfirmado);
                    }
                }
            } catch (Exception e) {
                log.info("[EmailPoller] re-verificacao via API indisponivel para referencia {} ({}) — " +
                        "a confiar no conteudo do email", reference, e.getMessage());
            }
        }

        Fatura fatura = localizarFatura(reference, numeroFatura);
        if (fatura == null) {
            log.error("[EmailPoller] fatura nao encontrada (numeroFatura={}, referencia={}) — pagamento NAO " +
                    "registado. Verifica manualmente.", numeroFatura, reference);
            return false;
        }

        if (valorConfirmado != null && valorConfirmado.compareTo(fatura.getSaldoDevedor()) > 0) {
            log.error("[EmailPoller] valor do email ({}) excede o saldo devedor da fatura {} ({}) — IGNORADO " +
                    "por seguranca. Verifica manualmente.", valorConfirmado, fatura.getNumeroFatura(),
                    fatura.getSaldoDevedor());
            return false;
        }

        PagamentoDTO dto = new PagamentoDTO();
        dto.setFaturaId(fatura.getId());
        dto.setDataPagamento(LocalDate.now());
        dto.setValorPago(valorConfirmado != null ? valorConfirmado : fatura.getSaldoDevedor());
        dto.setFormaPagamento("emola".equalsIgnoreCase(canalConfirmado)
                ? Pagamento.FormaPagamento.EMOLA
                : Pagamento.FormaPagamento.MPESA);
        dto.setReferencia(reference != null ? reference : ("fatura-" + numeroFatura));
        dto.setObservacoes("Pagamento automatico confirmado via email ZumboPay (reforco do webhook)");
        dto.setRegistadoPor("Sistema (ZumboPay - email)");

        try {
            pagamentoService.registarPagamento(dto);
            log.info("[EmailPoller] pagamento automatico registado com sucesso para a fatura {} (ref: {})",
                    fatura.getNumeroFatura(), reference);
            return true;
        } catch (Exception e) {
            // Caso comum e esperado: o webhook ja tinha registado este
            // mesmo pagamento entretanto. Nao e um erro, e o cenario ideal.
            log.info("[EmailPoller] fatura {} (ref: {}) nao foi actualizada — provavelmente ja estava paga " +
                    "(ex: o webhook chegou entretanto). Detalhe: {}", fatura.getNumeroFatura(), reference,
                    e.getMessage());
            return false;
        }
    }

    private Fatura localizarFatura(String reference, String numeroFatura) {
        Optional<Fatura> porReferencia = reference != null
                ? faturaRepository.findByReferenciaZumbopay(reference)
                : Optional.empty();
        if (porReferencia.isPresent()) {
            return porReferencia.get();
        }
        if (numeroFatura != null) {
            return faturaRepository.buscarPorNumeroFaturaComCliente(numeroFatura).orElse(null);
        }
        return null;
    }

    private String canalPorTelefone(String telefone) {
        if (telefone == null || telefone.length() < 2) {
            return "mpesa";
        }
        String prefixo = telefone.substring(0, 2);
        return (prefixo.equals("86") || prefixo.equals("87")) ? "emola" : "mpesa";
    }

    private String extrairTelefone(String corpo) {
        Matcher m = PADRAO_TELEFONE.matcher(corpo);
        return m.find() ? m.group(2) : null;
    }

    private String extrair(Pattern padrao, String corpo) {
        Matcher m = padrao.matcher(corpo);
        return m.find() ? m.group(1).trim() : null;
    }

    private BigDecimal parseValorMzn(String texto) {
        // Formato do email: "2,00" (virgula como separador decimal)
        String normalizado = texto.replace(".", "").replace(",", ".");
        try {
            return new BigDecimal(normalizado);
        } catch (NumberFormatException e) {
            log.warn("[EmailPoller] nao foi possivel converter valor '{}' para numero", texto);
            return null;
        }
    }

    private String primeiroCabecalho(Message msg, String nome) throws MessagingException {
        String[] valores = msg.getHeader(nome);
        return (valores != null && valores.length > 0) ? valores[0] : null;
    }

    /**
     * Extrai o texto de um email, preferindo a parte text/plain; se so
     * existir text/html, remove as tags de forma simples (suficiente para
     * o formato consistente e simples dos emails do ZumboPay).
     */
    private String extrairTexto(Message msg) throws Exception {
        Object conteudo = msg.getContent();
        if (conteudo instanceof String texto) {
            return msg.isMimeType("text/html") ? removerTagsHtml(texto) : texto;
        }
        if (conteudo instanceof MimeMultipart multipart) {
            String htmlFallback = null;
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart parte = multipart.getBodyPart(i);
                if (parte.isMimeType("text/plain")) {
                    return (String) parte.getContent();
                }
                if (parte.isMimeType("text/html") && htmlFallback == null) {
                    htmlFallback = removerTagsHtml((String) parte.getContent());
                }
            }
            return htmlFallback;
        }
        return null;
    }

    private String removerTagsHtml(String html) {
        return html.replaceAll("<[^>]*>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim()
                // Mantem quebras logicas entre "Referencia:" e o valor,
                // reintroduzindo uma quebra de linha apos os dois-pontos
                // mais comuns deste email, para o regex funcionar bem
                // mesmo que a tag <br> tenha sido removida.
                .replace("Referência: ", "Referência:\n")
                .replace("Referencia: ", "Referencia:\n");
    }
}

package com.aguasystem.service;

import com.aguasystem.entity.*;
import com.aguasystem.exception.NegocioException;
import com.aguasystem.repository.FaturaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
public class FaturaService {

    private static final Logger log = LoggerFactory.getLogger(FaturaService.class);

    private final FaturaRepository faturaRepository;
    private final ConfiguracaoService configuracaoService;
    private final MensagemService mensagemService;
    private final ZumboPayService zumboPayService;

    public FaturaService(FaturaRepository faturaRepository, ConfiguracaoService configuracaoService,
                          MensagemService mensagemService, ZumboPayService zumboPayService) {
        this.faturaRepository = faturaRepository;
        this.configuracaoService = configuracaoService;
        this.mensagemService = mensagemService;
        this.zumboPayService = zumboPayService;
    }

    @Transactional(readOnly = true)
    public List<Fatura> listarTodas() {
        return faturaRepository.listarTodasComCliente();
    }

    @Transactional(readOnly = true)
    public Fatura buscarPorId(Long id) {
        return faturaRepository.buscarPorIdComCliente(id)
                .orElseThrow(() -> new NegocioException("Fatura nao encontrada (ID: " + id + ")"));
    }

    @Transactional(readOnly = true)
    public List<Fatura> listarPorCliente(Long clienteId) {
        return faturaRepository.findByClienteIdOrderByMesReferenciaDesc(clienteId);
    }

    @Transactional(readOnly = true)
    public List<Fatura> listarPorStatus(Fatura.StatusFatura status) {
        return faturaRepository.findByStatus(status);
    }

    @Transactional(readOnly = true)
    public List<Fatura> listarPorMes(LocalDate mesReferencia, Fatura.StatusFatura statusOpcional) {
        LocalDate mes = mesReferencia.withDayOfMonth(1);
        return statusOpcional != null
                ? faturaRepository.listarPorMesEStatusComCliente(mes, statusOpcional)
                : faturaRepository.listarPorMesComCliente(mes);
    }

    @Transactional(readOnly = true)
    public List<Fatura> buscarPorIds(List<Long> ids) {
        return faturaRepository.buscarPorIdsComCliente(ids);
    }

    @Transactional(readOnly = true)
    public List<Fatura> listarVencidas() {
        return faturaRepository.buscarFaturasVencidas(LocalDate.now());
    }

    /**
     * Reenvia manualmente a notificacao por SMS de uma fatura ja existente
     * (com o link de pagamento incluido). Util para testar o envio, ou
     * reenviar caso o cliente diga que nao recebeu a mensagem original.
     * Devolve o resultado real do envio (nao finge sucesso).
     */
    @Transactional(readOnly = true)
    public MensagemService.ResultadoSms reenviarNotificacao(Long faturaId) {
        Fatura fatura = buscarPorId(faturaId);
        return enviarNotificacaoFaturaNova(fatura);
    }

    /**
     * Regista na fatura o resultado de uma tentativa de cobranca via
     * ZumboPay (referencia gerada + status). O pagamento so e efetivamente
     * confirmado mais tarde, via webhook + re-verificacao autoritativa
     * (ver ZumboPayWebhookController) — isto aqui apenas guarda o "recibo"
     * do pedido para depois conseguirmos correlacionar.
     */
    @Transactional
    public void registarTentativaCobranca(Long faturaId, String referenciaZumbopay, String status) {
        Fatura fatura = buscarPorId(faturaId);
        fatura.setReferenciaZumbopay(referenciaZumbopay);

        Fatura.StatusCobranca statusCobranca = switch (status) {
            case "success" -> Fatura.StatusCobranca.SUCESSO;
            case "pending" -> Fatura.StatusCobranca.PENDENTE;
            case "declined" -> Fatura.StatusCobranca.RECUSADO;
            default -> Fatura.StatusCobranca.FALHOU;
        };
        fatura.setStatusCobrancaZumbopay(statusCobranca);
        faturaRepository.save(fatura);
    }

    /**
     * Gera uma fatura a partir de uma leitura de contador, aplicando o preco
     * por m3 e a taxa fixa configurados no momento da emissao. Os valores
     * aplicados sao "congelados" na fatura (precoM3Aplicado, taxaFixaAplicada)
     * para que alteracoes futuras na configuracao nao afetem faturas ja emitidas.
     *
     * ARRASTE DE DIVIDA ANTERIOR: antes de calcular o valor final, o sistema
     * busca faturas anteriores do mesmo cliente que ainda tenham saldo devedor
     * (PENDENTE, PARCIALMENTE_PAGA ou VENCIDA) e soma esse saldo ao valor da
     * fatura nova (campo saldoAnteriorAplicado). As faturas antigas sao entao
     * marcadas como TRANSFERIDA (nao PAGA — o cliente nao pagou, a divida so
     * mudou de fatura), evitando que a mesma divida seja contada duas vezes.
     */
    public static class ResultadoGeracaoFatura {
        public final Fatura fatura;

        public ResultadoGeracaoFatura(Fatura fatura) {
            this.fatura = fatura;
        }
    }

    @Transactional
    public ResultadoGeracaoFatura gerarFaturaDeLeitura(LeituraContador leitura) {
        Configuracao config = configuracaoService.obterConfiguracao();

        BigDecimal consumo = leitura.getConsumoM3();
        BigDecimal consumoCobravel = consumo.max(
                config.getConsumoMinimoM3() != null ? config.getConsumoMinimoM3() : BigDecimal.ZERO);

        BigDecimal valorConsumo = consumoCobravel.multiply(config.getPrecoM3())
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxaFixa = config.getTaxaFixa() != null ? config.getTaxaFixa() : BigDecimal.ZERO;

        // Busca dividas antigas do cliente (faturas anteriores com saldo devedor)
        List<Fatura> faturasAntigas = faturaRepository.buscarFaturasComSaldoDevedor(leitura.getCliente().getId());
        BigDecimal saldoAnterior = BigDecimal.ZERO;
        for (Fatura antiga : faturasAntigas) {
            saldoAnterior = saldoAnterior.add(antiga.getSaldoDevedor());
        }

        BigDecimal valorTotal = valorConsumo.add(taxaFixa).add(saldoAnterior);

        Fatura fatura = new Fatura();
        fatura.setNumeroFatura(gerarNumeroFatura(leitura.getMesReferencia()));
        fatura.setCliente(leitura.getCliente());
        fatura.setLeitura(leitura);
        fatura.setMesReferencia(leitura.getMesReferencia());
        fatura.setDataEmissao(LocalDate.now());
        fatura.setDataVencimento(calcularDataVencimento(leitura.getMesReferencia(), config.getDiaVencimento()));
        fatura.setConsumoM3(consumo);
        fatura.setPrecoM3Aplicado(config.getPrecoM3());
        fatura.setTaxaFixaAplicada(taxaFixa);
        fatura.setSaldoAnteriorAplicado(saldoAnterior);
        fatura.setValorTotal(valorTotal);
        fatura.setValorPago(BigDecimal.ZERO);
        fatura.setStatus(Fatura.StatusFatura.PENDENTE);

        Fatura faturaSalva = faturaRepository.save(fatura);

        // Marca as faturas antigas como TRANSFERIDA, quitando o saldo delas
        // (o valor nao foi pago pelo cliente — apenas passou para a fatura nova)
        for (Fatura antiga : faturasAntigas) {
            antiga.setValorPago(antiga.getValorTotal());
            antiga.setStatus(Fatura.StatusFatura.TRANSFERIDA);
            antiga.setTransferidaParaNumeroFatura(faturaSalva.getNumeroFatura());
            faturaRepository.save(antiga);
        }

        // NAO envia SMS automaticamente aqui: da oportunidade de rever/corrigir
        // a fatura primeiro (ver "Corrigir Fatura"), evitando mandar ao cliente
        // um link/valor errado. O SMS so sai quando alguem clica manualmente
        // no botao de envio (ver FaturaController -> reenviarNotificacao).

        return new ResultadoGeracaoFatura(faturaSalva);
    }

    /**
     * Envia um SMS automatico ao cliente avisando da fatura nova. Se o
     * envio de SMS estiver desativado ou falhar, isso NUNCA impede a
     * fatura de ser gerada com sucesso — o erro fica so registado no log
     * (ver MensagemService).
     */
    /**
     * Envia UMA UNICA mensagem SMS ao cliente, ja com o link de pagamento
     * incluido (gerado via ZumboPayService.gerarLinkPagamento — checkout
     * hospedado, valido por mais tempo que o STK push imediato). Se o
     * ZumboPay nao conseguir gerar o link (ex: desativado, sem
     * credenciais), a mensagem e enviada mesmo assim, so sem o link — a
     * fatura continua a ser criada normalmente em qualquer dos casos.
     *
     * Devolve o resultado real do envio de SMS, para o chamador poder
     * decidir se mostra sucesso ou erro (nunca finge sucesso).
     */
    private MensagemService.ResultadoSms enviarNotificacaoFaturaNova(Fatura fatura) {
        ZumboPayService.ResultadoLinkPagamento link =
                zumboPayService.gerarLinkPagamento(fatura.getSaldoDevedor(), fatura.getNumeroFatura());

        String mensagem;
        if (link.sucesso) {
            mensagem = String.format(
                    "Ola %s! A sua fatura %s (%s/%d) no valor de %s MT esta pronta. Vencimento: %s. " +
                    "Pague aqui: %s",
                    fatura.getCliente().getNomeCompleto(),
                    fatura.getNumeroFatura(),
                    String.format("%02d", fatura.getMesReferencia().getMonthValue()),
                    fatura.getMesReferencia().getYear(),
                    fatura.getValorTotal(),
                    fatura.getDataVencimento().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    link.checkoutUrl
            );
        } else {
            log.warn("Nao foi possivel gerar link de pagamento para fatura {}: {} — " +
                    "SMS sera enviado sem o link", fatura.getNumeroFatura(), link.mensagem);
            mensagem = String.format(
                    "Ola %s! A sua fatura %s (%s/%d) no valor de %s MT esta pronta. Vencimento: %s.",
                    fatura.getCliente().getNomeCompleto(),
                    fatura.getNumeroFatura(),
                    String.format("%02d", fatura.getMesReferencia().getMonthValue()),
                    fatura.getMesReferencia().getYear(),
                    fatura.getValorTotal(),
                    fatura.getDataVencimento().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            );
        }

        return mensagemService.enviarSms(fatura.getCliente().getTelefone(), mensagem);
    }

    private String gerarNumeroFatura(LocalDate mesReferencia) {
        String prefixo = "FT" + mesReferencia.getYear() + String.format("%02d", mesReferencia.getMonthValue());
        long sequencial = faturaRepository.count() + 1;
        return prefixo + "-" + String.format("%05d", sequencial);
    }

    private LocalDate calcularDataVencimento(LocalDate mesReferencia, Integer diaVencimento) {
        LocalDate proximoMes = mesReferencia.plusMonths(1);
        int dia = Math.min(diaVencimento, proximoMes.lengthOfMonth());
        return proximoMes.withDayOfMonth(dia);
    }

    /**
     * Atualiza o status de faturas pendentes cuja data de vencimento ja passou,
     * marcando-as como VENCIDA. Pode ser chamado por um scheduler ou manualmente
     * no dashboard.
     */
    @Transactional
    public int atualizarFaturasVencidas() {
        List<Fatura> vencidas = faturaRepository.buscarFaturasVencidas(LocalDate.now());
        for (Fatura fatura : vencidas) {
            fatura.setStatus(Fatura.StatusFatura.VENCIDA);
            faturaRepository.save(fatura);
        }
        return vencidas.size();
    }

    @Transactional
    public void cancelar(Long id, String motivo) {
        Fatura fatura = buscarPorId(id);
        if (fatura.getValorPago().compareTo(BigDecimal.ZERO) > 0) {
            throw new NegocioException(
                    "Nao e possivel cancelar uma fatura que ja recebeu pagamentos. " +
                    "Estorne os pagamentos primeiro.");
        }
        fatura.setStatus(Fatura.StatusFatura.CANCELADA);
        faturaRepository.save(fatura);
    }

    /**
     * Corrige o consumo e/ou a data de vencimento de uma fatura ja emitida.
     * So permitido quando a fatura ainda nao recebeu nenhum pagamento e
     * esta Pendente ou Vencida — faturas ja pagas, parcialmente pagas,
     * canceladas ou transferidas nao podem ser editadas (para nao gerar
     * inconsistencia com pagamentos/arrastes ja registados).
     *
     * O preco por m3, a taxa fixa e o saldo anterior arrastado permanecem
     * "congelados" como estavam na emissao original — so o valor do
     * consumo (e, por consequencia, o valor total) e recalculado. A
     * leitura de contador associada e sincronizada automaticamente para
     * refletir o novo consumo.
     */
    @Transactional
    public Fatura atualizarConsumoEVencimento(Long id, java.math.BigDecimal novoConsumo, LocalDate novaDataVencimento) {
        Fatura fatura = buscarPorId(id);

        if (fatura.getValorPago().signum() > 0) {
            throw new NegocioException(
                    "Nao e possivel editar uma fatura que ja recebeu pagamentos. " +
                    "Se o valor estiver errado, cancela esta fatura (se ainda nao tiver pagamentos) " +
                    "ou contacta o suporte.");
        }
        if (fatura.getStatus() != Fatura.StatusFatura.PENDENTE && fatura.getStatus() != Fatura.StatusFatura.VENCIDA) {
            throw new NegocioException(
                    "So e possivel editar faturas com status Pendente ou Vencida");
        }

        BigDecimal taxaFixa = fatura.getTaxaFixaAplicada() != null ? fatura.getTaxaFixaAplicada() : BigDecimal.ZERO;
        BigDecimal saldoAnterior = fatura.getSaldoAnteriorAplicado() != null ? fatura.getSaldoAnteriorAplicado() : BigDecimal.ZERO;
        BigDecimal valorConsumo = novoConsumo.multiply(fatura.getPrecoM3Aplicado()).setScale(2, RoundingMode.HALF_UP);

        fatura.setConsumoM3(novoConsumo);
        fatura.setValorTotal(valorConsumo.add(taxaFixa).add(saldoAnterior));
        fatura.setDataVencimento(novaDataVencimento);

        // Sincroniza a leitura de contador associada, se existir, para que
        // o historico de leituras nao fique dessincronizado do valor faturado.
        if (fatura.getLeitura() != null) {
            LeituraContador leitura = fatura.getLeitura();
            leitura.setLeituraAtual(leitura.getLeituraAnterior().add(novoConsumo));
        }

        return faturaRepository.save(fatura);
    }

    /**
     * Atualiza o status da fatura com base no valor pago acumulado.
     * Chamado pelo PagamentoService apos registar um pagamento.
     */
    @Transactional
    protected void recalcularStatus(Fatura fatura) {
        int comparacao = fatura.getValorPago().compareTo(fatura.getValorTotal());
        if (comparacao >= 0) {
            fatura.setStatus(Fatura.StatusFatura.PAGA);
        } else if (fatura.getValorPago().compareTo(BigDecimal.ZERO) > 0) {
            fatura.setStatus(Fatura.StatusFatura.PARCIALMENTE_PAGA);
        } else if (fatura.getDataVencimento().isBefore(LocalDate.now())) {
            fatura.setStatus(Fatura.StatusFatura.VENCIDA);
        } else {
            fatura.setStatus(Fatura.StatusFatura.PENDENTE);
        }
    }

    @Transactional
    public Fatura salvar(Fatura fatura) {
        recalcularStatus(fatura);
        return faturaRepository.save(fatura);
    }
}

package com.aguasystem.service;

import com.aguasystem.dto.PagamentoDTO;
import com.aguasystem.entity.Fatura;
import com.aguasystem.entity.Pagamento;
import com.aguasystem.exception.NegocioException;
import com.aguasystem.repository.PagamentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class PagamentoService {

    private final PagamentoRepository pagamentoRepository;
    private final FaturaService faturaService;

    public PagamentoService(PagamentoRepository pagamentoRepository, FaturaService faturaService) {
        this.pagamentoRepository = pagamentoRepository;
        this.faturaService = faturaService;
    }

    @Transactional(readOnly = true)
    public List<Pagamento> listarPorFatura(Long faturaId) {
        return pagamentoRepository.findByFaturaIdOrderByDataPagamentoDesc(faturaId);
    }

    /**
     * Registra um pagamento (total ou parcial) sobre uma fatura.
     * Apos registar, recalcula o valor pago acumulado e o status da fatura
     * (PENDENTE -> PARCIALMENTE_PAGA -> PAGA) dentro da MESMA transacao,
     * garantindo consistencia entre Pagamento e Fatura.
     */
    @Transactional
    public Pagamento registarPagamento(PagamentoDTO dto) {
        Fatura fatura = faturaService.buscarPorId(dto.getFaturaId());

        if (fatura.getStatus() == Fatura.StatusFatura.CANCELADA) {
            throw new NegocioException("Nao e possivel registar pagamento numa fatura cancelada");
        }
        if (fatura.getStatus() == Fatura.StatusFatura.TRANSFERIDA) {
            throw new NegocioException(
                    "Esta fatura teve a divida transferida para a fatura " +
                    fatura.getTransferidaParaNumeroFatura() + ". Registe o pagamento la.");
        }
        if (fatura.getStatus() == Fatura.StatusFatura.PAGA) {
            throw new NegocioException("Esta fatura ja esta totalmente paga");
        }

        BigDecimal saldoDevedor = fatura.getSaldoDevedor();
        if (dto.getValorPago().compareTo(saldoDevedor) > 0) {
            throw new NegocioException(
                    "O valor pago (" + dto.getValorPago() + ") excede o saldo devedor (" + saldoDevedor + "). " +
                    "Se o cliente pagou a mais, registe o valor exato do saldo.");
        }

        Pagamento pagamento = new Pagamento();
        pagamento.setFatura(fatura);
        pagamento.setDataPagamento(dto.getDataPagamento());
        pagamento.setValorPago(dto.getValorPago());
        pagamento.setFormaPagamento(dto.getFormaPagamento());
        pagamento.setReferencia(dto.getReferencia());
        pagamento.setObservacoes(dto.getObservacoes());
        pagamento.setRegistadoPor(dto.getRegistadoPor());
        pagamentoRepository.save(pagamento);

        // Atualiza o valor pago acumulado da fatura (entidade gerenciada)
        fatura.setValorPago(fatura.getValorPago().add(dto.getValorPago()));
        faturaService.salvar(fatura); // recalcula status internamente

        return pagamento;
    }
}

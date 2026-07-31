package com.aguasystem.service;

import com.aguasystem.entity.Fatura;
import com.aguasystem.repository.ClienteRepository;
import com.aguasystem.repository.FaturaRepository;
import com.aguasystem.repository.PagamentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class DashboardService {

    private final ClienteRepository clienteRepository;
    private final FaturaRepository faturaRepository;
    private final PagamentoRepository pagamentoRepository;

    public DashboardService(ClienteRepository clienteRepository, FaturaRepository faturaRepository,
                             PagamentoRepository pagamentoRepository) {
        this.clienteRepository = clienteRepository;
        this.faturaRepository = faturaRepository;
        this.pagamentoRepository = pagamentoRepository;
    }

    @Transactional(readOnly = true)
    public EstatisticasDashboard obterEstatisticas() {
        LocalDate mesAtual = LocalDate.now().withDayOfMonth(1);
        LocalDate inicioProximoMes = mesAtual.plusMonths(1);

        EstatisticasDashboard stats = new EstatisticasDashboard();
        stats.setTotalClientesAtivos(clienteRepository.countByAtivoTrue());
        stats.setTotalFaturasPendentes(faturaRepository.countByStatus(Fatura.StatusFatura.PENDENTE));
        stats.setTotalFaturasVencidas(faturaRepository.countByStatus(Fatura.StatusFatura.VENCIDA));
        stats.setTotalFaturasPagas(faturaRepository.countByStatus(Fatura.StatusFatura.PAGA));
        stats.setValorFaturadoMesAtual(faturaRepository.somarValorFaturadoNoMes(mesAtual));
        // Usa os registos REAIS de Pagamento, nao SUM(Fatura.valorPago) —
        // esse campo fica inflado quando uma fatura e marcada como
        // TRANSFERIDA (divida arrastada, nunca paga pelo cliente).
        stats.setValorRecebidoMesAtual(
                pagamentoRepository.somarValorRecebidoNoPeriodo(mesAtual, inicioProximoMes));

        BigDecimal faturado = stats.getValorFaturadoMesAtual();
        BigDecimal taxaCobranca = faturado.compareTo(BigDecimal.ZERO) > 0
                ? stats.getValorRecebidoMesAtual()
                        .multiply(BigDecimal.valueOf(100))
                        .divide(faturado, 1, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        stats.setTaxaCobrancaPercentual(taxaCobranca);

        return stats;
    }

    public static class EstatisticasDashboard {
        private long totalClientesAtivos;
        private long totalFaturasPendentes;
        private long totalFaturasVencidas;
        private long totalFaturasPagas;
        private BigDecimal valorFaturadoMesAtual = BigDecimal.ZERO;
        private BigDecimal valorRecebidoMesAtual = BigDecimal.ZERO;
        private BigDecimal taxaCobrancaPercentual = BigDecimal.ZERO;

        public long getTotalClientesAtivos() { return totalClientesAtivos; }
        public void setTotalClientesAtivos(long v) { this.totalClientesAtivos = v; }

        public long getTotalFaturasPendentes() { return totalFaturasPendentes; }
        public void setTotalFaturasPendentes(long v) { this.totalFaturasPendentes = v; }

        public long getTotalFaturasVencidas() { return totalFaturasVencidas; }
        public void setTotalFaturasVencidas(long v) { this.totalFaturasVencidas = v; }

        public long getTotalFaturasPagas() { return totalFaturasPagas; }
        public void setTotalFaturasPagas(long v) { this.totalFaturasPagas = v; }

        public BigDecimal getValorFaturadoMesAtual() { return valorFaturadoMesAtual; }
        public void setValorFaturadoMesAtual(BigDecimal v) { this.valorFaturadoMesAtual = v; }

        public BigDecimal getValorRecebidoMesAtual() { return valorRecebidoMesAtual; }
        public void setValorRecebidoMesAtual(BigDecimal v) { this.valorRecebidoMesAtual = v; }

        public BigDecimal getTaxaCobrancaPercentual() { return taxaCobrancaPercentual; }
        public void setTaxaCobrancaPercentual(BigDecimal v) { this.taxaCobrancaPercentual = v; }
    }
}

package com.aguasystem.service;

import com.aguasystem.repository.FaturaRepository;
import com.aguasystem.repository.PagamentoRepository;
import com.aguasystem.repository.projection.PagamentoRealClienteProjection;
import com.aguasystem.repository.projection.SaldoClienteProjection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RelatorioService {

    private final FaturaRepository faturaRepository;
    private final PagamentoRepository pagamentoRepository;

    public RelatorioService(FaturaRepository faturaRepository, PagamentoRepository pagamentoRepository) {
        this.faturaRepository = faturaRepository;
        this.pagamentoRepository = pagamentoRepository;
    }

    /**
     * Monta o relatorio de dividas: separa clientes com saldo devedor
     * (devedores) dos clientes que ja pagaram tudo o que foi faturado
     * (em dia).
     *
     * O "Total Pago" usa os registos REAIS da tabela Pagamento (dinheiro
     * que entrou de verdade), nao o campo Fatura.valorPago — que pode ficar
     * artificialmente igual ao valorTotal quando uma fatura e marcada como
     * TRANSFERIDA (arraste automatico de divida). Faturas TRANSFERIDA sao
     * excluidas de ambos os totais (faturado e pago), porque o saldo delas
     * ja foi incorporado integralmente na fatura mais recente do cliente.
     */
    @Transactional(readOnly = true)
    public RelatorioDividas obterRelatorioDividas() {
        List<SaldoClienteProjection> agregadosFaturado = faturaRepository.agruparSaldoPorCliente();
        List<PagamentoRealClienteProjection> agregadosPago = pagamentoRepository.agruparPagamentoRealPorCliente();

        Map<Long, BigDecimal> mapaPagoReal = new HashMap<>();
        for (PagamentoRealClienteProjection p : agregadosPago) {
            mapaPagoReal.put(p.getClienteId(), p.getTotalPagoReal() != null ? p.getTotalPagoReal() : BigDecimal.ZERO);
        }

        List<ClienteSaldo> devedores = new ArrayList<>();
        List<ClienteSaldo> clientesEmDia = new ArrayList<>();
        BigDecimal totalDivida = BigDecimal.ZERO;
        BigDecimal totalRecebido = BigDecimal.ZERO;

        for (SaldoClienteProjection p : agregadosFaturado) {
            BigDecimal faturado = p.getTotalFaturado() != null ? p.getTotalFaturado() : BigDecimal.ZERO;
            BigDecimal pago = mapaPagoReal.getOrDefault(p.getClienteId(), BigDecimal.ZERO);
            BigDecimal saldo = faturado.subtract(pago);

            totalRecebido = totalRecebido.add(pago);

            ClienteSaldo cs = new ClienteSaldo();
            cs.setNomeCompleto(p.getNomeCompleto());
            cs.setNumeroContador(p.getNumeroContador());
            cs.setTotalFaturado(faturado);
            cs.setTotalPago(pago);
            cs.setSaldoDevedor(saldo);

            if (saldo.signum() > 0) {
                devedores.add(cs);
                totalDivida = totalDivida.add(saldo);
            } else {
                clientesEmDia.add(cs);
            }
        }

        // Maiores devedores primeiro
        devedores.sort(Comparator.comparing(ClienteSaldo::getSaldoDevedor).reversed());
        clientesEmDia.sort(Comparator.comparing(ClienteSaldo::getNomeCompleto));

        RelatorioDividas relatorio = new RelatorioDividas();
        relatorio.setDevedores(devedores);
        relatorio.setClientesEmDia(clientesEmDia);
        relatorio.setTotalDivida(totalDivida);
        relatorio.setTotalRecebido(totalRecebido);
        return relatorio;
    }

    public static class ClienteSaldo {
        private String nomeCompleto;
        private String numeroContador;
        private BigDecimal totalFaturado;
        private BigDecimal totalPago;
        private BigDecimal saldoDevedor;

        public String getNomeCompleto() { return nomeCompleto; }
        public void setNomeCompleto(String nomeCompleto) { this.nomeCompleto = nomeCompleto; }

        public String getNumeroContador() { return numeroContador; }
        public void setNumeroContador(String numeroContador) { this.numeroContador = numeroContador; }

        public BigDecimal getTotalFaturado() { return totalFaturado; }
        public void setTotalFaturado(BigDecimal totalFaturado) { this.totalFaturado = totalFaturado; }

        public BigDecimal getTotalPago() { return totalPago; }
        public void setTotalPago(BigDecimal totalPago) { this.totalPago = totalPago; }

        public BigDecimal getSaldoDevedor() { return saldoDevedor; }
        public void setSaldoDevedor(BigDecimal saldoDevedor) { this.saldoDevedor = saldoDevedor; }
    }

    public static class RelatorioDividas {
        private List<ClienteSaldo> devedores;
        private List<ClienteSaldo> clientesEmDia;
        private BigDecimal totalDivida;
        private BigDecimal totalRecebido;

        public List<ClienteSaldo> getDevedores() { return devedores; }
        public void setDevedores(List<ClienteSaldo> devedores) { this.devedores = devedores; }

        public List<ClienteSaldo> getClientesEmDia() { return clientesEmDia; }
        public void setClientesEmDia(List<ClienteSaldo> clientesEmDia) { this.clientesEmDia = clientesEmDia; }

        public BigDecimal getTotalDivida() { return totalDivida; }
        public void setTotalDivida(BigDecimal totalDivida) { this.totalDivida = totalDivida; }

        public BigDecimal getTotalRecebido() { return totalRecebido; }
        public void setTotalRecebido(BigDecimal totalRecebido) { this.totalRecebido = totalRecebido; }

        public int getQuantidadeDevedores() { return devedores != null ? devedores.size() : 0; }
        public int getQuantidadeEmDia() { return clientesEmDia != null ? clientesEmDia.size() : 0; }
    }
}

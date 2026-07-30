package com.aguasystem.repository;

import com.aguasystem.entity.Pagamento;
import com.aguasystem.repository.projection.PagamentoRealClienteProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PagamentoRepository extends JpaRepository<Pagamento, Long> {

    List<Pagamento> findByFaturaIdOrderByDataPagamentoDesc(Long faturaId);

    /**
     * Busca todos os pagamentos de um cliente (atraves das suas faturas),
     * do mais recente para o mais antigo. Usa JOIN FETCH pelo mesmo motivo
     * dos outros repositorios (open-in-view=false): sem o fetch, o Thymeleaf
     * geraria LazyInitializationException ao aceder pagamento.fatura na
     * pagina de historico do cliente.
     */
    @Query("SELECT p FROM Pagamento p JOIN FETCH p.fatura f WHERE f.cliente.id = :clienteId " +
           "ORDER BY p.dataPagamento DESC, p.dataRegisto DESC")
    List<Pagamento> findByClienteIdOrderByDataPagamentoDesc(Long clienteId);

    /**
     * Agrega o total REAL recebido em dinheiro por cliente, a partir dos
     * registos de Pagamento (nunca do campo Fatura.valorPago, que fica
     * "inflado" artificialmente quando uma fatura e marcada como TRANSFERIDA
     * — ver FaturaService.gerarFaturaDeLeitura). So considera pagamentos de
     * faturas ainda "vivas" (nao TRANSFERIDA, nao CANCELADA), para nao contar
     * o mesmo dinheiro duas vezes quando uma divida e arrastada.
     */
    @Query("SELECT f.cliente.id AS clienteId, SUM(p.valorPago) AS totalPagoReal " +
           "FROM Pagamento p JOIN p.fatura f " +
           "WHERE f.status NOT IN ('CANCELADA', 'TRANSFERIDA') " +
           "GROUP BY f.cliente.id")
    List<PagamentoRealClienteProjection> agruparPagamentoRealPorCliente();
}

package com.aguasystem.repository;

import com.aguasystem.entity.Fatura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FaturaRepository extends JpaRepository<Fatura, Long> {

    Optional<Fatura> findByNumeroFatura(String numeroFatura);

    /**
     * As consultas abaixo usam JOIN FETCH para trazer o Cliente junto com a
     * Fatura na mesma query. Necessario porque 'cliente' e LAZY e a app usa
     * open-in-view=false — sem o FETCH, o Thymeleaf tentaria acessar
     * fatura.cliente depois da transacao ja ter fechado, causando
     * LazyInitializationException ao renderizar a pagina.
     */
    @Query("SELECT f FROM Fatura f JOIN FETCH f.cliente ORDER BY f.dataEmissao DESC")
    List<Fatura> listarTodasComCliente();

    @Query("SELECT f FROM Fatura f JOIN FETCH f.cliente WHERE f.id = :id")
    Optional<Fatura> buscarPorIdComCliente(Long id);

    @Query("SELECT f FROM Fatura f JOIN FETCH f.cliente WHERE f.cliente.id = :clienteId " +
           "ORDER BY f.mesReferencia DESC")
    List<Fatura> findByClienteIdOrderByMesReferenciaDesc(Long clienteId);

    @Query("SELECT f FROM Fatura f JOIN FETCH f.cliente WHERE f.status = :status")
    List<Fatura> findByStatus(Fatura.StatusFatura status);

    List<Fatura> findByMesReferencia(LocalDate mesReferencia);

    @Query("SELECT f FROM Fatura f JOIN FETCH f.cliente c WHERE f.mesReferencia = :mesReferencia " +
           "ORDER BY c.nomeCompleto ASC")
    List<Fatura> listarPorMesComCliente(LocalDate mesReferencia);

    @Query("SELECT f FROM Fatura f JOIN FETCH f.cliente c WHERE f.mesReferencia = :mesReferencia " +
           "AND f.status = :status ORDER BY c.nomeCompleto ASC")
    List<Fatura> listarPorMesEStatusComCliente(LocalDate mesReferencia, Fatura.StatusFatura status);

    @Query("SELECT f FROM Fatura f JOIN FETCH f.cliente WHERE f.id IN :ids")
    List<Fatura> buscarPorIdsComCliente(List<Long> ids);

    @Query("SELECT f FROM Fatura f JOIN FETCH f.cliente WHERE f.status IN ('PENDENTE', 'PARCIALMENTE_PAGA') " +
           "AND f.dataVencimento < :hoje")
    List<Fatura> buscarFaturasVencidas(LocalDate hoje);

    @Query("SELECT COALESCE(SUM(f.valorTotal), 0) FROM Fatura f WHERE f.mesReferencia = :mes")
    java.math.BigDecimal somarValorFaturadoNoMes(LocalDate mes);

    @Query("SELECT COALESCE(SUM(f.valorPago), 0) FROM Fatura f WHERE f.mesReferencia = :mes")
    java.math.BigDecimal somarValorRecebidoNoMes(LocalDate mes);

    long countByStatus(Fatura.StatusFatura status);

    /**
     * Verifica se uma leitura ja gerou uma fatura. Usado para bloquear a
     * edicao direta de leituras que ja foram faturadas — nesse caso, a
     * correcao deve ser feita editando a propria fatura (que sincroniza
     * de volta a leitura).
     */
    Optional<Fatura> findByLeituraId(Long leituraId);

    @Query("SELECT f FROM Fatura f JOIN FETCH f.cliente WHERE f.referenciaZumbopay = :referencia")
    Optional<Fatura> findByReferenciaZumbopay(String referencia);

    @Query("SELECT f FROM Fatura f JOIN FETCH f.cliente WHERE f.numeroFatura = :numeroFatura")
    Optional<Fatura> buscarPorNumeroFaturaComCliente(String numeroFatura);

    /**
     * Busca faturas anteriores de um cliente que ainda tem saldo devedor
     * (nao pagas, parcialmente pagas ou vencidas), da mais antiga para a
     * mais recente. Usada ao gerar uma fatura nova para arrastar
     * automaticamente as dividas antigas para a fatura atual.
     */
    @Query("SELECT f FROM Fatura f WHERE f.cliente.id = :clienteId " +
           "AND f.status IN ('PENDENTE', 'PARCIALMENTE_PAGA', 'VENCIDA') " +
           "ORDER BY f.dataEmissao ASC")
    List<Fatura> buscarFaturasComSaldoDevedor(Long clienteId);

    /**
     * Agrega o total faturado por cliente, considerando apenas faturas
     * "vivas" (nao TRANSFERIDA, nao CANCELADA). Faturas TRANSFERIDA sao
     * excluidas porque o valor delas ja foi somado na fatura mais recente
     * atraves do arraste automatico de divida — contá-las de novo aqui
     * duplicaria o valor faturado do cliente.
     */
    @Query("SELECT f.cliente.id AS clienteId, f.cliente.nomeCompleto AS nomeCompleto, " +
           "f.cliente.numeroContador AS numeroContador, " +
           "SUM(f.valorTotal) AS totalFaturado " +
           "FROM Fatura f WHERE f.status NOT IN ('CANCELADA', 'TRANSFERIDA') " +
           "GROUP BY f.cliente.id, f.cliente.nomeCompleto, f.cliente.numeroContador")
    List<com.aguasystem.repository.projection.SaldoClienteProjection> agruparSaldoPorCliente();
}

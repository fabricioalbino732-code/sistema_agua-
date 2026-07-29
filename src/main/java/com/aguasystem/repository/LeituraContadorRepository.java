package com.aguasystem.repository;

import com.aguasystem.entity.Cliente;
import com.aguasystem.entity.LeituraContador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LeituraContadorRepository extends JpaRepository<LeituraContador, Long> {

    Optional<LeituraContador> findByClienteAndMesReferencia(Cliente cliente, LocalDate mesReferencia);

    boolean existsByClienteAndMesReferencia(Cliente cliente, LocalDate mesReferencia);

    /**
     * Usa JOIN FETCH para carregar o Cliente junto com a Leitura na mesma consulta.
     * Necessario porque 'cliente' e LAZY e a aplicacao usa open-in-view=false —
     * sem o FETCH, a pagina Thymeleaf tentaria acessar leitura.cliente depois da
     * transacao ja ter fechado, causando LazyInitializationException.
     */
    @Query("SELECT l FROM LeituraContador l JOIN FETCH l.cliente c " +
           "WHERE l.mesReferencia = :mesReferencia ORDER BY c.nomeCompleto ASC")
    List<LeituraContador> findByMesReferenciaOrderByClienteNomeCompletoAsc(LocalDate mesReferencia);

    @Query("SELECT l FROM LeituraContador l JOIN FETCH l.cliente " +
           "WHERE l.cliente.id = :clienteId ORDER BY l.mesReferencia DESC")
    List<LeituraContador> findByClienteIdOrderByMesReferenciaDesc(Long clienteId);

    /**
     * Busca a leitura mais recente de um cliente antes de um determinado mes,
     * usada para pre-preencher automaticamente a "leitura anterior" na proxima leitura.
     */
    Optional<LeituraContador> findFirstByClienteIdAndMesReferenciaLessThanOrderByMesReferenciaDesc(
            Long clienteId, LocalDate mesReferencia);

    /**
     * Busca uma leitura por ID ja com o Cliente carregado (JOIN FETCH).
     * Necessario ao gerar uma fatura a partir da leitura: como o cliente e
     * acedido dentro de OUTRA transacao (no FaturaService), sem o fetch join
     * o proxy LAZY estaria desanexado e geraria LazyInitializationException.
     */
    @Query("SELECT l FROM LeituraContador l JOIN FETCH l.cliente WHERE l.id = :id")
    Optional<LeituraContador> buscarPorIdComCliente(Long id);
}

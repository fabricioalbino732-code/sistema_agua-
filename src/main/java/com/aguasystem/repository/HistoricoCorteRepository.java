package com.aguasystem.repository;

import com.aguasystem.entity.HistoricoCorte;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface HistoricoCorteRepository extends JpaRepository<HistoricoCorte, Long> {

    List<HistoricoCorte> findByClienteIdOrderByDataCorteDesc(Long clienteId);

    Optional<HistoricoCorte> findFirstByClienteIdAndStatusOrderByDataCorteDesc(
            Long clienteId, HistoricoCorte.StatusCorte status);

    /**
     * JOIN FETCH necessario porque 'cliente' e LAZY e a pagina corte/lista.html
     * acessa corte.cliente.nomeCompleto depois da transacao do service ja ter
     * terminado (open-in-view=false).
     */
    @Query("SELECT h FROM HistoricoCorte h JOIN FETCH h.cliente WHERE h.status = :status")
    List<HistoricoCorte> findByStatus(HistoricoCorte.StatusCorte status);
}

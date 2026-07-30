package com.aguasystem.repository;

import com.aguasystem.entity.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    Optional<Cliente> findByNumeroContador(String numeroContador);

    boolean existsByNumeroContador(String numeroContador);

    List<Cliente> findByAtivoTrueOrderByNumeroContadorAsc();

    @Query("SELECT c FROM Cliente c WHERE " +
           "LOWER(c.nomeCompleto) LIKE LOWER(CONCAT('%', :termo, '%')) OR " +
           "c.numeroContador LIKE CONCAT('%', :termo, '%') " +
           "ORDER BY c.numeroContador ASC")
    List<Cliente> buscarPorNomeOuContador(@Param("termo") String termo);

    List<Cliente> findAllByOrderByNumeroContadorAsc();

    long countByAtivoTrue();
}

package com.aguasystem.repository.projection;

import java.math.BigDecimal;

/**
 * Projecao usada para agregar o total faturado por cliente, a partir de
 * uma consulta JPQL com GROUP BY. Usada no relatorio de dividas.
 *
 * IMPORTANTE: esta projecao considera apenas faturas "vivas" (nao
 * TRANSFERIDA, nao CANCELADA). Faturas TRANSFERIDA sao excluidas porque o
 * valor delas ja foi incorporado na fatura mais recente (via arraste de
 * divida) — inclui-las de novo aqui duplicaria o valor faturado.
 */
public interface SaldoClienteProjection {

    Long getClienteId();

    String getNomeCompleto();

    String getNumeroContador();

    BigDecimal getTotalFaturado();
}

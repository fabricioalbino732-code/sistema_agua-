package com.aguasystem.repository.projection;

import java.math.BigDecimal;

/**
 * Projecao que agrega o valor REAL recebido em dinheiro por cliente,
 * baseado nos registos da tabela Pagamento (nao no campo Fatura.valorPago,
 * que pode estar "inflado" artificialmente em faturas TRANSFERIDA — ver
 * FaturaService.gerarFaturaDeLeitura). Usada no relatorio de dividas para
 * mostrar o dinheiro efetivamente recebido, sem duplicar valores do
 * mecanismo de arraste de divida.
 */
public interface PagamentoRealClienteProjection {

    Long getClienteId();

    BigDecimal getTotalPagoReal();
}

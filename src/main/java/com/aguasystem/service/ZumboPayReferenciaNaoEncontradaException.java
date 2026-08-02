package com.aguasystem.service;

/**
 * Lancada quando GET /payments/{reference} devolve 404 no ZumboPay.
 *
 * Isto e esperado para referencias geradas por POST /charges (STK push,
 * formato "ZUMBO..."), ja que a documentacao do ZumboPay so descreve
 * GET /payments/{reference} para referencias de links de pagamento (POST
 * /payments, formato "ZP_..."). O ZumboPayWebhookController usa esta
 * excecao para decidir quando aplicar o fallback de confiar no proprio
 * payload do webhook em vez de insistir numa re-verificacao que a API
 * nao suporta para este tipo de referencia.
 */
public class ZumboPayReferenciaNaoEncontradaException extends RuntimeException {
    public ZumboPayReferenciaNaoEncontradaException(String reference) {
        super("Referencia " + reference + " nao encontrada em GET /payments/{reference}");
    }
}

package com.aguasystem.exception;

/**
 * Excecao lancada quando uma regra de negocio e violada
 * (ex: leitura atual menor que a anterior, cliente ja cortado, etc).
 * Capturada globalmente pelo GlobalExceptionHandler.
 */
public class NegocioException extends RuntimeException {

    public NegocioException(String mensagem) {
        super(mensagem);
    }
}

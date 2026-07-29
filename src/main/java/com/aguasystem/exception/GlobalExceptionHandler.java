package com.aguasystem.exception;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Tratamento centralizado de excecoes de negocio para os controllers MVC
 * (Thymeleaf). Evita que cada controller precise repetir try/catch, e
 * garante uma mensagem de erro consistente exibida ao utilizador.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NegocioException.class)
    public String tratarNegocioException(NegocioException ex, Model model) {
        model.addAttribute("mensagemErro", ex.getMessage());
        return "erro/erro-negocio";
    }
}

package com.aguasystem.config;

import com.aguasystem.service.UsuarioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UsuarioService usuarioService;

    public DataSeeder(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @Override
    public void run(String... args) {
        boolean naoTinhaUsuarios = usuarioService.listarTodos().isEmpty();
        usuarioService.garantirAdminPadrao();

        if (naoTinhaUsuarios) {
            log.warn("======================================================");
            log.warn(" Utilizador administrador padrao criado:");
            log.warn("   username: admin");
            log.warn("   senha:    admin123");
            log.warn(" MUDA ESTA SENHA IMEDIATAMENTE apos o primeiro login!");
            log.warn("======================================================");
        }
    }
}

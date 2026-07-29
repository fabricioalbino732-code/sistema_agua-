package com.aguasystem.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.FormLoginConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.io.IOException;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Redireciona o utilizador para uma pagina diferente apos o login,
     * consoante o perfil: o LEITOR vai direto para a pagina de leitura
     * remota (nao tem acesso ao resto do sistema); os outros perfis vao
     * para o dashboard normal.
     */
    @Bean
    public AuthenticationSuccessHandler successHandler() {
        return (request, response, authentication) -> {
            boolean somenteLeitor = authentication.getAuthorities().stream()
                    .allMatch(a -> a.getAuthority().equals("ROLE_LEITOR"));

            if (somenteLeitor) {
                response.sendRedirect("/leituras/remota");
            } else {
                response.sendRedirect("/");
            }
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, AuthenticationSuccessHandler successHandler) throws Exception {
        http
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers(new AntPathRequestMatcher("/webhooks/**"))
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/js/**", "/login").permitAll()
                        .requestMatchers("/webhooks/**").permitAll()
                        .requestMatchers("/usuarios/**", "/backup/**").hasRole("ADMIN")
                        .requestMatchers("/leituras/remota", "/leituras/remota/**", "/leituras/foto/**")
                            .hasAnyRole("LEITOR", "OPERADOR", "ADMIN")
                        .requestMatchers("/leituras/**", "/clientes/**", "/faturas/**", "/pagamentos/**",
                                "/cortes/**", "/configuracao/**", "/relatorios/**", "/", "/dashboard/**")
                            .hasAnyRole("OPERADOR", "ADMIN")
                        .anyRequest().authenticated()
                )
                .formLogin((FormLoginConfigurer<HttpSecurity> form) -> form
                        .loginPage("/login")
                        .successHandler(successHandler)
                        .failureUrl("/login?erro")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                );

        return http.build();
    }
}

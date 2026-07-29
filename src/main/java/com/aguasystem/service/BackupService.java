package com.aguasystem.service;

import com.aguasystem.exception.NegocioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gera backups da base de dados PostgreSQL usando a ferramenta de linha de
 * comando 'pg_dump', que precisa de estar instalada e acessivel no PATH do
 * sistema operativo (normalmente ja vem junto com a instalacao do
 * PostgreSQL). O backup gerado e um ficheiro .sql com todos os comandos
 * necessarios para recriar a base de dados do zero (schema + dados).
 */
@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final Pattern JDBC_URL_PATTERN =
            Pattern.compile("jdbc:postgresql://([^:/]+)(:(\\d+))?/([^?]+)");

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${backup.pgdump.path:pg_dump}")
    private String pgDumpPath;

    /**
     * Gera o backup e devolve o conteudo do ficheiro .sql em memoria,
     * pronto para ser descarregado pelo utilizador.
     */
    public byte[] gerarBackup() {
        String[] dados = parsearJdbcUrl(jdbcUrl);
        String host = dados[0];
        String porta = dados[1];
        String baseDados = dados[2];

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    pgDumpPath, "-h", host, "-p", porta, "-U", username,
                    "--no-password", "-F", "p", baseDados);
            pb.environment().put("PGPASSWORD", password);

            Process processo = pb.start();
            byte[] saida = processo.getInputStream().readAllBytes();
            byte[] erroBytes = processo.getErrorStream().readAllBytes();
            boolean terminouATempo = processo.waitFor(2, java.util.concurrent.TimeUnit.MINUTES);

            if (!terminouATempo) {
                processo.destroyForcibly();
                throw new NegocioException("O backup demorou demasiado tempo e foi cancelado");
            }

            if (processo.exitValue() != 0) {
                String erro = new String(erroBytes);
                log.error("pg_dump falhou: {}", erro);
                throw new NegocioException(
                        "Nao foi possivel gerar o backup. Verifica se o 'pg_dump' esta instalado " +
                        "e acessivel no PATH do sistema. Detalhe: " + erro);
            }

            if (saida.length == 0) {
                throw new NegocioException("O backup foi gerado vazio — verifica a ligacao com a base de dados");
            }

            return saida;
        } catch (IOException e) {
            throw new NegocioException(
                    "Nao foi possivel executar o 'pg_dump'. Verifica se o PostgreSQL esta instalado " +
                    "e se o 'pg_dump' esta no PATH do sistema. Erro: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NegocioException("A geracao do backup foi interrompida");
        }
    }

    /**
     * Gera o backup e grava diretamente num ficheiro local, usado pelo
     * agendamento automatico diario (BackupScheduler).
     */
    public Path gerarBackupParaArquivo(Path pastaDestino) {
        try {
            Files.createDirectories(pastaDestino);
            byte[] conteudo = gerarBackup();

            String nomeFicheiro = "backup-" +
                    java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm")) + ".sql";
            Path caminho = pastaDestino.resolve(nomeFicheiro);
            Files.write(caminho, conteudo);
            return caminho;
        } catch (IOException e) {
            throw new NegocioException("Erro ao gravar ficheiro de backup: " + e.getMessage());
        }
    }

    private String[] parsearJdbcUrl(String url) {
        Matcher matcher = JDBC_URL_PATTERN.matcher(url);
        if (!matcher.find()) {
            throw new NegocioException("Nao foi possivel interpretar a URL da base de dados para gerar o backup");
        }
        String host = matcher.group(1);
        String porta = matcher.group(3) != null ? matcher.group(3) : "5432";
        String baseDados = matcher.group(4);
        return new String[]{host, porta, baseDados};
    }
}

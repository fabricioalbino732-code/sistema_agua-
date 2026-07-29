package com.aguasystem.config;

import com.aguasystem.service.BackupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Gera um backup automatico da base de dados todos os dias as 02:00 da
 * manha (horario de baixa utilizacao), desde que a aplicacao esteja em
 * execucao nesse momento. Mantem apenas os ultimos 14 backups para nao
 * encher o disco, apagando os mais antigos automaticamente.
 *
 * LIMITACAO IMPORTANTE: isto so funciona enquanto a aplicacao Spring Boot
 * estiver a correr. Se o computador for desligado ou a aplicacao fechada,
 * nao ha backup nesse dia. Para backups verdadeiramente garantidos mesmo
 * com a aplicacao desligada, o ideal e complementar com uma tarefa agendada
 * do sistema operativo (cron no Linux/Mac, Agendador de Tarefas no Windows)
 * chamando 'pg_dump' diretamente.
 */
@Component
public class BackupScheduler {

    private static final Logger log = LoggerFactory.getLogger(BackupScheduler.class);
    private static final int MAXIMO_BACKUPS_MANTIDOS = 14;

    private final BackupService backupService;

    @Value("${backup.pasta:./backups}")
    private String pastaBackup;

    public BackupScheduler(BackupService backupService) {
        this.backupService = backupService;
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void executarBackupAutomatico() {
        try {
            Path pasta = Paths.get(pastaBackup);
            Path arquivoGerado = backupService.gerarBackupParaArquivo(pasta);
            log.info("Backup automatico gerado com sucesso: {}", arquivoGerado);
            limparBackupsAntigos(pasta);
        } catch (Exception e) {
            log.error("Falha ao gerar backup automatico diario: {}", e.getMessage(), e);
        }
    }

    private void limparBackupsAntigos(Path pasta) {
        try {
            List<Path> arquivos = Files.list(pasta)
                    .filter(p -> p.getFileName().toString().startsWith("backup-"))
                    .sorted(Comparator.comparing(this::obterDataModificacao).reversed())
                    .collect(Collectors.toList());

            for (int i = MAXIMO_BACKUPS_MANTIDOS; i < arquivos.size(); i++) {
                Files.deleteIfExists(arquivos.get(i));
                log.info("Backup antigo removido: {}", arquivos.get(i));
            }
        } catch (Exception e) {
            log.warn("Nao foi possivel limpar backups antigos: {}", e.getMessage());
        }
    }

    private java.nio.file.attribute.FileTime obterDataModificacao(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (Exception e) {
            return java.nio.file.attribute.FileTime.fromMillis(0);
        }
    }
}

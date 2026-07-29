package com.aguasystem.service;

import com.aguasystem.exception.NegocioException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Guarda ficheiros (fotos de leituras de contador) no disco local, fora da
 * base de dados. A base de dados guarda apenas o CAMINHO do ficheiro.
 *
 * Motivo de nao guardar na base de dados: fotos sao binarias e pesadas —
 * guarda-las na base de dados faria o backup (pg_dump) crescer muito e
 * ficar lento, alem de tornar consultas normais mais pesadas.
 */
@Service
public class FileStorageService {

    @Value("${fotos.pasta:./fotos-leituras}")
    private String pastaFotos;

    /**
     * Guarda a foto enviada e devolve o nome do ficheiro gerado (nao o
     * caminho completo), para ser armazenado na base de dados.
     */
    public String guardarFotoLeitura(MultipartFile foto) {
        if (foto == null || foto.isEmpty()) {
            throw new NegocioException("A foto do contador e obrigatoria");
        }

        String extensao = obterExtensao(foto.getOriginalFilename());
        String nomeFicheiro = "leitura-" + UUID.randomUUID() + extensao;

        try {
            Path pasta = Paths.get(pastaFotos);
            Files.createDirectories(pasta);

            Path destino = pasta.resolve(nomeFicheiro);
            Files.copy(foto.getInputStream(), destino, StandardCopyOption.REPLACE_EXISTING);

            return nomeFicheiro;
        } catch (IOException e) {
            throw new NegocioException("Erro ao guardar a foto: " + e.getMessage());
        }
    }

    /**
     * Le o conteudo de uma foto ja guardada, para ser servida atraves de um
     * endpoint autenticado (nunca diretamente por URL publica/estatica).
     */
    public byte[] lerFoto(String nomeFicheiro) {
        try {
            Path caminho = Paths.get(pastaFotos).resolve(nomeFicheiro);
            if (!Files.exists(caminho)) {
                throw new NegocioException("Foto nao encontrada");
            }
            return Files.readAllBytes(caminho);
        } catch (IOException e) {
            throw new NegocioException("Erro ao ler a foto: " + e.getMessage());
        }
    }

    private String obterExtensao(String nomeOriginal) {
        if (nomeOriginal == null || !nomeOriginal.contains(".")) {
            return ".jpg";
        }
        return nomeOriginal.substring(nomeOriginal.lastIndexOf('.'));
    }
}

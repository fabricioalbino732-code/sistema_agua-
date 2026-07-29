package com.aguasystem.service;

import com.aguasystem.dto.LeituraDTO;
import com.aguasystem.dto.LeituraRemotaDTO;
import com.aguasystem.entity.Cliente;
import com.aguasystem.entity.LeituraContador;
import com.aguasystem.exception.NegocioException;
import com.aguasystem.repository.FaturaRepository;
import com.aguasystem.repository.LeituraContadorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class LeituraService {

    private final LeituraContadorRepository leituraRepository;
    private final ClienteService clienteService;
    private final FaturaRepository faturaRepository;
    private final FileStorageService fileStorageService;

    public LeituraService(LeituraContadorRepository leituraRepository, ClienteService clienteService,
                           FaturaRepository faturaRepository, FileStorageService fileStorageService) {
        this.leituraRepository = leituraRepository;
        this.clienteService = clienteService;
        this.faturaRepository = faturaRepository;
        this.fileStorageService = fileStorageService;
    }

    @Transactional(readOnly = true)
    public List<LeituraContador> listarPorMes(LocalDate mesReferencia) {
        return leituraRepository.findByMesReferenciaOrderByClienteNomeCompletoAsc(
                normalizarParaPrimeiroDiaDoMes(mesReferencia));
    }

    @Transactional(readOnly = true)
    public List<LeituraContador> listarPorCliente(Long clienteId) {
        return leituraRepository.findByClienteIdOrderByMesReferenciaDesc(clienteId);
    }

    @Transactional(readOnly = true)
    public LeituraContador buscarPorId(Long id) {
        return leituraRepository.buscarPorIdComCliente(id)
                .orElseThrow(() -> new NegocioException("Leitura nao encontrada (ID: " + id + ")"));
    }

    /**
     * Sugere a leitura anterior automaticamente com base na ultima leitura
     * registada do cliente, para reduzir erro de digitacao manual.
     */
    @Transactional(readOnly = true)
    public BigDecimal sugerirLeituraAnterior(Long clienteId, LocalDate mesReferencia) {
        LocalDate mes = normalizarParaPrimeiroDiaDoMes(mesReferencia);
        Optional<LeituraContador> ultima = leituraRepository
                .findFirstByClienteIdAndMesReferenciaLessThanOrderByMesReferenciaDesc(clienteId, mes);
        return ultima.map(LeituraContador::getLeituraAtual).orElse(BigDecimal.ZERO);
    }

    @Transactional
    public LeituraContador registar(LeituraDTO dto) {
        Cliente cliente = clienteService.buscarPorId(dto.getClienteId());
        LocalDate mes = normalizarParaPrimeiroDiaDoMes(dto.getMesReferencia());

        if (leituraRepository.existsByClienteAndMesReferencia(cliente, mes)) {
            throw new NegocioException(
                    "Ja existe uma leitura registada para este cliente no mes " + mes.getMonthValue() + "/" + mes.getYear());
        }

        validarLeituras(dto.getLeituraAnterior(), dto.getLeituraAtual());

        LeituraContador leitura = new LeituraContador();
        leitura.setCliente(cliente);
        leitura.setMesReferencia(mes);
        leitura.setDataLeitura(dto.getDataLeitura());
        leitura.setLeituraAnterior(dto.getLeituraAnterior());
        leitura.setLeituraAtual(dto.getLeituraAtual());
        leitura.setResponsavelLeitura(dto.getResponsavelLeitura());
        leitura.setObservacoes(dto.getObservacoes());

        return leituraRepository.save(leitura);
    }

    @Transactional(readOnly = true)
    public boolean temFaturaGerada(Long leituraId) {
        return faturaRepository.findByLeituraId(leituraId).isPresent();
    }

    @Transactional
    public LeituraContador atualizar(Long id, LeituraDTO dto) {
        LeituraContador leitura = buscarPorId(id); // entidade gerenciada

        if (temFaturaGerada(id)) {
            throw new NegocioException(
                    "Esta leitura ja gerou uma fatura e nao pode ser editada diretamente. " +
                    "Para corrigir o consumo, edita a fatura correspondente (ela sincroniza a leitura).");
        }

        validarLeituras(dto.getLeituraAnterior(), dto.getLeituraAtual());

        leitura.setDataLeitura(dto.getDataLeitura());
        leitura.setLeituraAnterior(dto.getLeituraAnterior());
        leitura.setLeituraAtual(dto.getLeituraAtual());
        leitura.setResponsavelLeitura(dto.getResponsavelLeitura());
        leitura.setObservacoes(dto.getObservacoes());

        return leituraRepository.save(leitura);
    }

    /**
     * Regista uma leitura feita remotamente pelo fiscalizador, no telemovel.
     * Usa o mes atual automaticamente e a leitura anterior e sugerida sem
     * o leitor precisar de a digitar. Exige foto do contador como
     * comprovativo, e guarda a localizacao GPS captada no momento (se o
     * navegador do telemovel a disponibilizar), para confirmar que o
     * leitor esteve mesmo na residencia do cliente.
     */
    @Transactional
    public LeituraContador registarRemota(LeituraRemotaDTO dto) {
        Cliente cliente = clienteService.buscarPorId(dto.getClienteId());
        LocalDate mes = normalizarParaPrimeiroDiaDoMes(LocalDate.now());

        if (leituraRepository.existsByClienteAndMesReferencia(cliente, mes)) {
            throw new NegocioException(
                    "Ja existe uma leitura registada para este cliente este mes");
        }

        BigDecimal leituraAnterior = sugerirLeituraAnterior(dto.getClienteId(), mes);
        validarLeituras(leituraAnterior, dto.getLeituraAtual());

        String nomeFicheiroFoto = fileStorageService.guardarFotoLeitura(dto.getFoto());

        LeituraContador leitura = new LeituraContador();
        leitura.setCliente(cliente);
        leitura.setMesReferencia(mes);
        leitura.setDataLeitura(LocalDate.now());
        leitura.setLeituraAnterior(leituraAnterior);
        leitura.setLeituraAtual(dto.getLeituraAtual());
        leitura.setFotoContadorPath(nomeFicheiroFoto);
        leitura.setLatitude(dto.getLatitude());
        leitura.setLongitude(dto.getLongitude());
        leitura.setOrigem(LeituraContador.OrigemLeitura.REMOTA);

        return leituraRepository.save(leitura);
    }

    private void validarLeituras(BigDecimal anterior, BigDecimal atual) {
        if (atual.compareTo(anterior) < 0) {
            throw new NegocioException(
                    "A leitura atual (" + atual + ") nao pode ser menor que a leitura anterior (" + anterior + ")");
        }
    }

    private LocalDate normalizarParaPrimeiroDiaDoMes(LocalDate data) {
        return data.withDayOfMonth(1);
    }
}

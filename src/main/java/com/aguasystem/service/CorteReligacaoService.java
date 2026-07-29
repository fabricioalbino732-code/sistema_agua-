package com.aguasystem.service;

import com.aguasystem.entity.Cliente;
import com.aguasystem.entity.HistoricoCorte;
import com.aguasystem.exception.NegocioException;
import com.aguasystem.repository.HistoricoCorteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class CorteReligacaoService {

    private final HistoricoCorteRepository historicoCorteRepository;
    private final ClienteService clienteService;

    public CorteReligacaoService(HistoricoCorteRepository historicoCorteRepository,
                                  ClienteService clienteService) {
        this.historicoCorteRepository = historicoCorteRepository;
        this.clienteService = clienteService;
    }

    @Transactional(readOnly = true)
    public List<HistoricoCorte> listarPorCliente(Long clienteId) {
        return historicoCorteRepository.findByClienteIdOrderByDataCorteDesc(clienteId);
    }

    @Transactional(readOnly = true)
    public List<HistoricoCorte> listarCortadosAtualmente() {
        return historicoCorteRepository.findByStatus(HistoricoCorte.StatusCorte.CORTADO);
    }

    @Transactional(readOnly = true)
    public boolean estaCortado(Long clienteId) {
        return historicoCorteRepository
                .findFirstByClienteIdAndStatusOrderByDataCorteDesc(clienteId, HistoricoCorte.StatusCorte.CORTADO)
                .isPresent();
    }

    @Transactional
    public HistoricoCorte registarCorte(Long clienteId, String motivo, String registadoPor) {
        if (estaCortado(clienteId)) {
            throw new NegocioException("Este cliente ja se encontra cortado");
        }

        Cliente cliente = clienteService.buscarPorId(clienteId);

        HistoricoCorte corte = new HistoricoCorte();
        corte.setCliente(cliente);
        corte.setDataCorte(LocalDate.now());
        corte.setMotivo(motivo);
        corte.setStatus(HistoricoCorte.StatusCorte.CORTADO);
        corte.setRegistadoPor(registadoPor);

        return historicoCorteRepository.save(corte);
    }

    @Transactional
    public HistoricoCorte registarReligacao(Long clienteId, String registadoPor) {
        Optional<HistoricoCorte> corteAtivo = historicoCorteRepository
                .findFirstByClienteIdAndStatusOrderByDataCorteDesc(clienteId, HistoricoCorte.StatusCorte.CORTADO);

        HistoricoCorte corte = corteAtivo.orElseThrow(
                () -> new NegocioException("Este cliente nao se encontra cortado atualmente"));

        corte.setDataReligacao(LocalDate.now());
        corte.setStatus(HistoricoCorte.StatusCorte.RELIGADO);
        // registadoPor aqui refere-se a quem fez a religacao; mantemos o registo
        // original de quem fez o corte no campo original e anotamos a religacao
        // nas observacoes para preservar o historico de auditoria.
        corte.setMotivo(corte.getMotivo() + " | Religado por: " + registadoPor);

        return historicoCorteRepository.save(corte);
    }
}

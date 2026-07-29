package com.aguasystem.service;

import com.aguasystem.dto.ClienteDTO;
import com.aguasystem.entity.Cliente;
import com.aguasystem.exception.NegocioException;
import com.aguasystem.repository.ClienteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ClienteService {

    private final ClienteRepository clienteRepository;

    public ClienteService(ClienteRepository clienteRepository) {
        this.clienteRepository = clienteRepository;
    }

    @Transactional(readOnly = true)
    public List<Cliente> listarTodos() {
        return clienteRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Cliente> listarAtivos() {
        return clienteRepository.findByAtivoTrue();
    }

    @Transactional(readOnly = true)
    public Cliente buscarPorId(Long id) {
        return clienteRepository.findById(id)
                .orElseThrow(() -> new NegocioException("Cliente nao encontrado (ID: " + id + ")"));
    }

    @Transactional(readOnly = true)
    public List<Cliente> buscar(String termo) {
        if (termo == null || termo.isBlank()) {
            return listarTodos();
        }
        return clienteRepository.buscarPorNomeOuContador(termo.trim());
    }

    @Transactional
    public Cliente criar(ClienteDTO dto) {
        if (clienteRepository.existsByNumeroContador(dto.getNumeroContador())) {
            throw new NegocioException(
                    "Ja existe um cliente registado com o numero de contador " + dto.getNumeroContador());
        }

        Cliente cliente = new Cliente();
        aplicarDadosDto(cliente, dto);
        return clienteRepository.save(cliente);
    }

    @Transactional
    public Cliente atualizar(Long id, ClienteDTO dto) {
        Cliente cliente = buscarPorId(id); // entidade gerenciada

        // Se o numero do contador mudou, validar que o novo numero nao esta em uso
        if (!cliente.getNumeroContador().equals(dto.getNumeroContador())
                && clienteRepository.existsByNumeroContador(dto.getNumeroContador())) {
            throw new NegocioException(
                    "Ja existe outro cliente com o numero de contador " + dto.getNumeroContador());
        }

        aplicarDadosDto(cliente, dto);
        return clienteRepository.save(cliente);
    }

    private void aplicarDadosDto(Cliente cliente, ClienteDTO dto) {
        cliente.setNomeCompleto(dto.getNomeCompleto());
        cliente.setNumeroContador(dto.getNumeroContador());
        cliente.setBairro(dto.getBairro());
        cliente.setEndereco(dto.getEndereco());
        cliente.setTelefone(dto.getTelefone());
        cliente.setEmail(dto.getEmail());
        cliente.setTipoCliente(dto.getTipoCliente());
        cliente.setAtivo(dto.getAtivo() != null ? dto.getAtivo() : true);
        cliente.setDataLigacao(dto.getDataLigacao());
        cliente.setObservacoes(dto.getObservacoes());
    }

    @Transactional
    public void inativar(Long id) {
        Cliente cliente = buscarPorId(id);
        cliente.setAtivo(false);
        clienteRepository.save(cliente);
    }

    @Transactional(readOnly = true)
    public long contarAtivos() {
        return clienteRepository.countByAtivoTrue();
    }
}

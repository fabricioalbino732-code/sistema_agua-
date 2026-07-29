package com.aguasystem.dto;

import com.aguasystem.entity.Cliente;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

public class ClienteDTO {

    private Long id;

    @NotBlank(message = "O nome e obrigatorio")
    @Size(max = 150)
    private String nomeCompleto;

    @NotBlank(message = "O numero do contador e obrigatorio")
    @Size(max = 30)
    private String numeroContador;

    @Size(max = 100)
    private String bairro;

    @Size(max = 255)
    private String endereco;

    @Size(max = 30)
    private String telefone;

    @Email(message = "Email invalido")
    private String email;

    @NotNull(message = "O tipo de cliente e obrigatorio")
    private Cliente.TipoCliente tipoCliente;

    private Boolean ativo = true;

    private LocalDate dataLigacao;

    @Size(max = 500)
    private String observacoes;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNomeCompleto() { return nomeCompleto; }
    public void setNomeCompleto(String nomeCompleto) { this.nomeCompleto = nomeCompleto; }

    public String getNumeroContador() { return numeroContador; }
    public void setNumeroContador(String numeroContador) { this.numeroContador = numeroContador; }

    public String getBairro() { return bairro; }
    public void setBairro(String bairro) { this.bairro = bairro; }

    public String getEndereco() { return endereco; }
    public void setEndereco(String endereco) { this.endereco = endereco; }

    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Cliente.TipoCliente getTipoCliente() { return tipoCliente; }
    public void setTipoCliente(Cliente.TipoCliente tipoCliente) { this.tipoCliente = tipoCliente; }

    public Boolean getAtivo() { return ativo; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }

    public LocalDate getDataLigacao() { return dataLigacao; }
    public void setDataLigacao(LocalDate dataLigacao) { this.dataLigacao = dataLigacao; }

    public String getObservacoes() { return observacoes; }
    public void setObservacoes(String observacoes) { this.observacoes = observacoes; }
}

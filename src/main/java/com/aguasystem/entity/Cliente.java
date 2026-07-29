package com.aguasystem.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "cliente")
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome_completo", nullable = false, length = 150)
    private String nomeCompleto;

    @Column(name = "numero_contador", nullable = false, unique = true, length = 30)
    private String numeroContador;

    @Column(name = "bairro", length = 100)
    private String bairro;

    @Column(name = "endereco", length = 255)
    private String endereco;

    @Column(name = "telefone", length = 30)
    private String telefone;

    @Column(name = "email", length = 100)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_cliente", nullable = false, length = 20, columnDefinition = "varchar(20)")
    private TipoCliente tipoCliente = TipoCliente.RESIDENCIAL;

    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;

    @Column(name = "data_cadastro", nullable = false, updatable = false)
    private LocalDateTime dataCadastro;

    @Column(name = "data_ligacao")
    private LocalDate dataLigacao;

    @Column(name = "observacoes", length = 500)
    private String observacoes;

    @PrePersist
    private void aoPersistir() {
        this.dataCadastro = LocalDateTime.now();
    }

    public enum TipoCliente {
        RESIDENCIAL, COMERCIAL, INDUSTRIAL, PUBLICO
    }

    public Cliente() {
    }

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

    public TipoCliente getTipoCliente() { return tipoCliente; }
    public void setTipoCliente(TipoCliente tipoCliente) { this.tipoCliente = tipoCliente; }

    public Boolean getAtivo() { return ativo; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }

    public LocalDateTime getDataCadastro() { return dataCadastro; }
    public void setDataCadastro(LocalDateTime dataCadastro) { this.dataCadastro = dataCadastro; }

    public LocalDate getDataLigacao() { return dataLigacao; }
    public void setDataLigacao(LocalDate dataLigacao) { this.dataLigacao = dataLigacao; }

    public String getObservacoes() { return observacoes; }
    public void setObservacoes(String observacoes) { this.observacoes = observacoes; }
}

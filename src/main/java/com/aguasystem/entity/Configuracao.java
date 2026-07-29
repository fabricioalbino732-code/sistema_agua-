package com.aguasystem.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * Entidade de configuracao global do sistema.
 *
 * IMPORTANTE: Esta entidade segue o padrao SINGLETON garantido a nivel de banco.
 * O ID e sempre fixo em 1 (ver constante ID_CONFIGURACAO). Isto evita o bug
 * historico onde cada "save" criava uma nova linha em vez de atualizar a
 * configuracao existente, fazendo o sistema "perder" os dados ao reiniciar
 * (o sistema lia sempre a PRIMEIRA linha, que ficava desatualizada).
 *
 * A garantia de singleton e feita em duas camadas:
 * 1. Aqui: o ID e fixado manualmente (nao e @GeneratedValue)
 * 2. No ConfiguracaoService: get/save sempre operam sobre o ID_CONFIGURACAO
 */
@Entity
@Table(name = "configuracao")
public class Configuracao {

    /** ID fixo do singleton. Nunca criar/buscar configuracao com outro ID. */
    public static final Long ID_CONFIGURACAO = 1L;

    @Id
    private Long id = ID_CONFIGURACAO;

    @Column(name = "nome_empresa", nullable = false, length = 150)
    private String nomeEmpresa;

    @Column(name = "nuit", length = 20)
    private String nuit;

    @Column(name = "endereco", length = 255)
    private String endereco;

    @Column(name = "telefone", length = 30)
    private String telefone;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "preco_m3", nullable = false, precision = 12, scale = 2)
    private BigDecimal precoM3 = BigDecimal.ZERO;

    @Column(name = "taxa_fixa", precision = 12, scale = 2)
    private BigDecimal taxaFixa = BigDecimal.ZERO;

    @Column(name = "dia_vencimento", nullable = false)
    private Integer diaVencimento = 10;

    @Column(name = "multa_atraso_percentual", precision = 5, scale = 2)
    private BigDecimal multaAtrasoPercentual = BigDecimal.ZERO;

    @Column(name = "consumo_minimo_m3", precision = 10, scale = 2)
    private BigDecimal consumoMinimoM3 = BigDecimal.ZERO;

    @Column(name = "logo_path", length = 255)
    private String logoPath;

    @PrePersist
    @PreUpdate
    private void garantirIdFixo() {
        this.id = ID_CONFIGURACAO;
    }

    public Configuracao() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNomeEmpresa() { return nomeEmpresa; }
    public void setNomeEmpresa(String nomeEmpresa) { this.nomeEmpresa = nomeEmpresa; }

    public String getNuit() { return nuit; }
    public void setNuit(String nuit) { this.nuit = nuit; }

    public String getEndereco() { return endereco; }
    public void setEndereco(String endereco) { this.endereco = endereco; }

    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public BigDecimal getPrecoM3() { return precoM3; }
    public void setPrecoM3(BigDecimal precoM3) { this.precoM3 = precoM3; }

    public BigDecimal getTaxaFixa() { return taxaFixa; }
    public void setTaxaFixa(BigDecimal taxaFixa) { this.taxaFixa = taxaFixa; }

    public Integer getDiaVencimento() { return diaVencimento; }
    public void setDiaVencimento(Integer diaVencimento) { this.diaVencimento = diaVencimento; }

    public BigDecimal getMultaAtrasoPercentual() { return multaAtrasoPercentual; }
    public void setMultaAtrasoPercentual(BigDecimal multaAtrasoPercentual) { this.multaAtrasoPercentual = multaAtrasoPercentual; }

    public BigDecimal getConsumoMinimoM3() { return consumoMinimoM3; }
    public void setConsumoMinimoM3(BigDecimal consumoMinimoM3) { this.consumoMinimoM3 = consumoMinimoM3; }

    public String getLogoPath() { return logoPath; }
    public void setLogoPath(String logoPath) { this.logoPath = logoPath; }
}

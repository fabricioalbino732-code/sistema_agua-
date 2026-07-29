package com.aguasystem.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public class ConfiguracaoDTO {

    @NotBlank(message = "O nome da empresa e obrigatorio")
    @Size(max = 150)
    private String nomeEmpresa;

    @Size(max = 20)
    private String nuit;

    @Size(max = 255)
    private String endereco;

    @Size(max = 30)
    private String telefone;

    @Email(message = "Email invalido")
    private String email;

    @NotNull(message = "O preco por m3 e obrigatorio")
    @DecimalMin(value = "0.01", message = "O preco por m3 deve ser maior que zero")
    private BigDecimal precoM3;

    @DecimalMin(value = "0.00")
    private BigDecimal taxaFixa;

    @NotNull(message = "O dia de vencimento e obrigatorio")
    @Min(value = 1, message = "O dia deve ser entre 1 e 28")
    @Max(value = 28, message = "O dia deve ser entre 1 e 28")
    private Integer diaVencimento;

    @DecimalMin(value = "0.00")
    @DecimalMax(value = "100.00")
    private BigDecimal multaAtrasoPercentual;

    @DecimalMin(value = "0.00")
    private BigDecimal consumoMinimoM3;

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
}

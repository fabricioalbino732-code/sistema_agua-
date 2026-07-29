package com.aguasystem.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public class LeituraDTO {

    private Long id;

    @NotNull(message = "O cliente e obrigatorio")
    private Long clienteId;

    @NotNull(message = "O mes de referencia e obrigatorio")
    private LocalDate mesReferencia;

    @NotNull(message = "A data da leitura e obrigatoria")
    private LocalDate dataLeitura;

    @NotNull(message = "A leitura anterior e obrigatoria")
    @DecimalMin(value = "0.00", message = "A leitura anterior nao pode ser negativa")
    private BigDecimal leituraAnterior;

    @NotNull(message = "A leitura atual e obrigatoria")
    @DecimalMin(value = "0.00", message = "A leitura atual nao pode ser negativa")
    private BigDecimal leituraAtual;

    @Size(max = 100)
    private String responsavelLeitura;

    @Size(max = 300)
    private String observacoes;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getClienteId() { return clienteId; }
    public void setClienteId(Long clienteId) { this.clienteId = clienteId; }

    public LocalDate getMesReferencia() { return mesReferencia; }
    public void setMesReferencia(LocalDate mesReferencia) { this.mesReferencia = mesReferencia; }

    public LocalDate getDataLeitura() { return dataLeitura; }
    public void setDataLeitura(LocalDate dataLeitura) { this.dataLeitura = dataLeitura; }

    public BigDecimal getLeituraAnterior() { return leituraAnterior; }
    public void setLeituraAnterior(BigDecimal leituraAnterior) { this.leituraAnterior = leituraAnterior; }

    public BigDecimal getLeituraAtual() { return leituraAtual; }
    public void setLeituraAtual(BigDecimal leituraAtual) { this.leituraAtual = leituraAtual; }

    public String getResponsavelLeitura() { return responsavelLeitura; }
    public void setResponsavelLeitura(String responsavelLeitura) { this.responsavelLeitura = responsavelLeitura; }

    public String getObservacoes() { return observacoes; }
    public void setObservacoes(String observacoes) { this.observacoes = observacoes; }
}

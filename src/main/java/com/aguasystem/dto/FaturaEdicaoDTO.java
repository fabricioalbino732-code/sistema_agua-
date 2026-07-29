package com.aguasystem.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public class FaturaEdicaoDTO {

    @NotNull(message = "O consumo e obrigatorio")
    @DecimalMin(value = "0.00", message = "O consumo nao pode ser negativo")
    private BigDecimal consumoM3;

    @NotNull(message = "A data de vencimento e obrigatoria")
    private LocalDate dataVencimento;

    public BigDecimal getConsumoM3() { return consumoM3; }
    public void setConsumoM3(BigDecimal consumoM3) { this.consumoM3 = consumoM3; }

    public LocalDate getDataVencimento() { return dataVencimento; }
    public void setDataVencimento(LocalDate dataVencimento) { this.dataVencimento = dataVencimento; }
}

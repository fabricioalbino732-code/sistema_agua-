package com.aguasystem.dto;

import com.aguasystem.entity.Pagamento;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public class PagamentoDTO {

    @NotNull(message = "A fatura e obrigatoria")
    private Long faturaId;

    @NotNull(message = "A data do pagamento e obrigatoria")
    private LocalDate dataPagamento;

    @NotNull(message = "O valor pago e obrigatorio")
    @DecimalMin(value = "0.01", message = "O valor pago deve ser maior que zero")
    private BigDecimal valorPago;

    @NotNull(message = "A forma de pagamento e obrigatoria")
    private Pagamento.FormaPagamento formaPagamento;

    @Size(max = 100)
    private String referencia;

    @Size(max = 300)
    private String observacoes;

    @Size(max = 100)
    private String registadoPor;

    public Long getFaturaId() { return faturaId; }
    public void setFaturaId(Long faturaId) { this.faturaId = faturaId; }

    public LocalDate getDataPagamento() { return dataPagamento; }
    public void setDataPagamento(LocalDate dataPagamento) { this.dataPagamento = dataPagamento; }

    public BigDecimal getValorPago() { return valorPago; }
    public void setValorPago(BigDecimal valorPago) { this.valorPago = valorPago; }

    public Pagamento.FormaPagamento getFormaPagamento() { return formaPagamento; }
    public void setFormaPagamento(Pagamento.FormaPagamento formaPagamento) { this.formaPagamento = formaPagamento; }

    public String getReferencia() { return referencia; }
    public void setReferencia(String referencia) { this.referencia = referencia; }

    public String getObservacoes() { return observacoes; }
    public void setObservacoes(String observacoes) { this.observacoes = observacoes; }

    public String getRegistadoPor() { return registadoPor; }
    public void setRegistadoPor(String registadoPor) { this.registadoPor = registadoPor; }
}

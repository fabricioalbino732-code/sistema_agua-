package com.aguasystem.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "pagamento")
public class Pagamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fatura_id", nullable = false)
    private Fatura fatura;

    @Column(name = "data_pagamento", nullable = false)
    private LocalDate dataPagamento;

    @Column(name = "valor_pago", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorPago;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento", nullable = false, length = 20, columnDefinition = "varchar(20)")
    private FormaPagamento formaPagamento;

    @Column(name = "referencia", length = 100)
    private String referencia;

    @Column(name = "observacoes", length = 300)
    private String observacoes;

    @Column(name = "registado_por", length = 100)
    private String registadoPor;

    @Column(name = "data_registo", nullable = false, updatable = false)
    private LocalDateTime dataRegisto;

    @PrePersist
    private void aoPersistir() {
        this.dataRegisto = LocalDateTime.now();
    }

    public enum FormaPagamento {
        NUMERARIO, MPESA, EMOLA, TRANSFERENCIA_BANCARIA, DEPOSITO
    }

    public Pagamento() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Fatura getFatura() { return fatura; }
    public void setFatura(Fatura fatura) { this.fatura = fatura; }

    public LocalDate getDataPagamento() { return dataPagamento; }
    public void setDataPagamento(LocalDate dataPagamento) { this.dataPagamento = dataPagamento; }

    public BigDecimal getValorPago() { return valorPago; }
    public void setValorPago(BigDecimal valorPago) { this.valorPago = valorPago; }

    public FormaPagamento getFormaPagamento() { return formaPagamento; }
    public void setFormaPagamento(FormaPagamento formaPagamento) { this.formaPagamento = formaPagamento; }

    public String getReferencia() { return referencia; }
    public void setReferencia(String referencia) { this.referencia = referencia; }

    public String getObservacoes() { return observacoes; }
    public void setObservacoes(String observacoes) { this.observacoes = observacoes; }

    public String getRegistadoPor() { return registadoPor; }
    public void setRegistadoPor(String registadoPor) { this.registadoPor = registadoPor; }

    public LocalDateTime getDataRegisto() { return dataRegisto; }
    public void setDataRegisto(LocalDateTime dataRegisto) { this.dataRegisto = dataRegisto; }
}

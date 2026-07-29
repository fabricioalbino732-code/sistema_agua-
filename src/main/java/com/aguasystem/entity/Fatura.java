package com.aguasystem.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "fatura")
public class Fatura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_fatura", nullable = false, unique = true, length = 30)
    private String numeroFatura;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leitura_id")
    private LeituraContador leitura;

    @Column(name = "mes_referencia", nullable = false)
    private LocalDate mesReferencia;

    @Column(name = "data_emissao", nullable = false)
    private LocalDate dataEmissao;

    @Column(name = "data_vencimento", nullable = false)
    private LocalDate dataVencimento;

    @Column(name = "consumo_m3", nullable = false, precision = 12, scale = 2)
    private BigDecimal consumoM3;

    @Column(name = "preco_m3_aplicado", nullable = false, precision = 12, scale = 2)
    private BigDecimal precoM3Aplicado;

    @Column(name = "taxa_fixa_aplicada", precision = 12, scale = 2)
    private BigDecimal taxaFixaAplicada = BigDecimal.ZERO;

    @Column(name = "multa_aplicada", precision = 12, scale = 2)
    private BigDecimal multaAplicada = BigDecimal.ZERO;

    @Column(name = "valor_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorTotal;

    @Column(name = "saldo_anterior_aplicado", precision = 12, scale = 2)
    private BigDecimal saldoAnteriorAplicado = BigDecimal.ZERO;

    @Column(name = "transferida_para_numero_fatura", length = 30)
    private String transferidaParaNumeroFatura;

    @Column(name = "referencia_zumbopay", length = 50)
    private String referenciaZumbopay;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_cobranca_zumbopay", length = 20, columnDefinition = "varchar(20)")
    private StatusCobranca statusCobrancaZumbopay;

    public enum StatusCobranca {
        PENDENTE, SUCESSO, RECUSADO, FALHOU
    }

    @Column(name = "valor_pago", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorPago = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20, columnDefinition = "varchar(20)")
    private StatusFatura status = StatusFatura.PENDENTE;

    @Column(name = "data_registo", nullable = false, updatable = false)
    private LocalDateTime dataRegisto;

    @PrePersist
    private void aoPersistir() {
        this.dataRegisto = LocalDateTime.now();
    }

    public BigDecimal getSaldoDevedor() {
        return valorTotal.subtract(valorPago);
    }

    public enum StatusFatura {
        PENDENTE, PARCIALMENTE_PAGA, PAGA, VENCIDA, CANCELADA, TRANSFERIDA
    }

    public Fatura() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNumeroFatura() { return numeroFatura; }
    public void setNumeroFatura(String numeroFatura) { this.numeroFatura = numeroFatura; }

    public Cliente getCliente() { return cliente; }
    public void setCliente(Cliente cliente) { this.cliente = cliente; }

    public LeituraContador getLeitura() { return leitura; }
    public void setLeitura(LeituraContador leitura) { this.leitura = leitura; }

    public LocalDate getMesReferencia() { return mesReferencia; }
    public void setMesReferencia(LocalDate mesReferencia) { this.mesReferencia = mesReferencia; }

    public LocalDate getDataEmissao() { return dataEmissao; }
    public void setDataEmissao(LocalDate dataEmissao) { this.dataEmissao = dataEmissao; }

    public LocalDate getDataVencimento() { return dataVencimento; }
    public void setDataVencimento(LocalDate dataVencimento) { this.dataVencimento = dataVencimento; }

    public BigDecimal getConsumoM3() { return consumoM3; }
    public void setConsumoM3(BigDecimal consumoM3) { this.consumoM3 = consumoM3; }

    public BigDecimal getPrecoM3Aplicado() { return precoM3Aplicado; }
    public void setPrecoM3Aplicado(BigDecimal precoM3Aplicado) { this.precoM3Aplicado = precoM3Aplicado; }

    public BigDecimal getTaxaFixaAplicada() { return taxaFixaAplicada; }
    public void setTaxaFixaAplicada(BigDecimal taxaFixaAplicada) { this.taxaFixaAplicada = taxaFixaAplicada; }

    public BigDecimal getMultaAplicada() { return multaAplicada; }
    public void setMultaAplicada(BigDecimal multaAplicada) { this.multaAplicada = multaAplicada; }

    public BigDecimal getValorTotal() { return valorTotal; }
    public void setValorTotal(BigDecimal valorTotal) { this.valorTotal = valorTotal; }

    public BigDecimal getSaldoAnteriorAplicado() { return saldoAnteriorAplicado; }
    public void setSaldoAnteriorAplicado(BigDecimal saldoAnteriorAplicado) { this.saldoAnteriorAplicado = saldoAnteriorAplicado; }

    public String getTransferidaParaNumeroFatura() { return transferidaParaNumeroFatura; }
    public void setTransferidaParaNumeroFatura(String transferidaParaNumeroFatura) { this.transferidaParaNumeroFatura = transferidaParaNumeroFatura; }

    public String getReferenciaZumbopay() { return referenciaZumbopay; }
    public void setReferenciaZumbopay(String referenciaZumbopay) { this.referenciaZumbopay = referenciaZumbopay; }

    public StatusCobranca getStatusCobrancaZumbopay() { return statusCobrancaZumbopay; }
    public void setStatusCobrancaZumbopay(StatusCobranca statusCobrancaZumbopay) { this.statusCobrancaZumbopay = statusCobrancaZumbopay; }

    public BigDecimal getValorPago() { return valorPago; }
    public void setValorPago(BigDecimal valorPago) { this.valorPago = valorPago; }

    public StatusFatura getStatus() { return status; }
    public void setStatus(StatusFatura status) { this.status = status; }

    public LocalDateTime getDataRegisto() { return dataRegisto; }
    public void setDataRegisto(LocalDateTime dataRegisto) { this.dataRegisto = dataRegisto; }
}

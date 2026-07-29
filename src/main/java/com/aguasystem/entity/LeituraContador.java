package com.aguasystem.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "leitura_contador",
       uniqueConstraints = @UniqueConstraint(columnNames = {"cliente_id", "mes_referencia"}))
public class LeituraContador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    /** Primeiro dia do mes a que a leitura se refere, ex: 2026-07-01 */
    @Column(name = "mes_referencia", nullable = false)
    private LocalDate mesReferencia;

    @Column(name = "data_leitura", nullable = false)
    private LocalDate dataLeitura;

    @Column(name = "leitura_anterior", nullable = false, precision = 12, scale = 2)
    private BigDecimal leituraAnterior;

    @Column(name = "leitura_atual", nullable = false, precision = 12, scale = 2)
    private BigDecimal leituraAtual;

    @Column(name = "consumo_m3", nullable = false, precision = 12, scale = 2)
    private BigDecimal consumoM3;

    @Column(name = "responsavel_leitura", length = 100)
    private String responsavelLeitura;

    @Column(name = "observacoes", length = 300)
    private String observacoes;

    @Column(name = "foto_contador_path", length = 255)
    private String fotoContadorPath;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem", nullable = false, length = 20, columnDefinition = "varchar(20)")
    private OrigemLeitura origem = OrigemLeitura.MANUAL;

    @Column(name = "data_registo", nullable = false, updatable = false)
    private LocalDateTime dataRegisto;

    public enum OrigemLeitura {
        MANUAL, REMOTA
    }

    @PrePersist
    private void aoPersistir() {
        this.dataRegisto = LocalDateTime.now();
        calcularConsumo();
    }

    @PreUpdate
    private void aoAtualizar() {
        calcularConsumo();
    }

    private void calcularConsumo() {
        if (leituraAtual != null && leituraAnterior != null) {
            this.consumoM3 = leituraAtual.subtract(leituraAnterior);
        }
    }

    public LeituraContador() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Cliente getCliente() { return cliente; }
    public void setCliente(Cliente cliente) { this.cliente = cliente; }

    public LocalDate getMesReferencia() { return mesReferencia; }
    public void setMesReferencia(LocalDate mesReferencia) { this.mesReferencia = mesReferencia; }

    public LocalDate getDataLeitura() { return dataLeitura; }
    public void setDataLeitura(LocalDate dataLeitura) { this.dataLeitura = dataLeitura; }

    public BigDecimal getLeituraAnterior() { return leituraAnterior; }
    public void setLeituraAnterior(BigDecimal leituraAnterior) { this.leituraAnterior = leituraAnterior; }

    public BigDecimal getLeituraAtual() { return leituraAtual; }
    public void setLeituraAtual(BigDecimal leituraAtual) { this.leituraAtual = leituraAtual; }

    public BigDecimal getConsumoM3() { return consumoM3; }
    public void setConsumoM3(BigDecimal consumoM3) { this.consumoM3 = consumoM3; }

    public String getResponsavelLeitura() { return responsavelLeitura; }
    public void setResponsavelLeitura(String responsavelLeitura) { this.responsavelLeitura = responsavelLeitura; }

    public String getObservacoes() { return observacoes; }
    public void setObservacoes(String observacoes) { this.observacoes = observacoes; }

    public String getFotoContadorPath() { return fotoContadorPath; }
    public void setFotoContadorPath(String fotoContadorPath) { this.fotoContadorPath = fotoContadorPath; }

    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }

    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }

    public OrigemLeitura getOrigem() { return origem; }
    public void setOrigem(OrigemLeitura origem) { this.origem = origem; }

    public LocalDateTime getDataRegisto() { return dataRegisto; }
    public void setDataRegisto(LocalDateTime dataRegisto) { this.dataRegisto = dataRegisto; }
}

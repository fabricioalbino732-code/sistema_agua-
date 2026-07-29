package com.aguasystem.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "historico_corte")
public class HistoricoCorte {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @Column(name = "data_corte", nullable = false)
    private LocalDate dataCorte;

    @Column(name = "data_religacao")
    private LocalDate dataReligacao;

    @Column(name = "motivo", length = 300)
    private String motivo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20, columnDefinition = "varchar(20)")
    private StatusCorte status = StatusCorte.CORTADO;

    @Column(name = "registado_por", length = 100)
    private String registadoPor;

    @Column(name = "data_registo", nullable = false, updatable = false)
    private LocalDateTime dataRegisto;

    @PrePersist
    private void aoPersistir() {
        this.dataRegisto = LocalDateTime.now();
    }

    public enum StatusCorte {
        CORTADO, RELIGADO
    }

    public HistoricoCorte() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Cliente getCliente() { return cliente; }
    public void setCliente(Cliente cliente) { this.cliente = cliente; }

    public LocalDate getDataCorte() { return dataCorte; }
    public void setDataCorte(LocalDate dataCorte) { this.dataCorte = dataCorte; }

    public LocalDate getDataReligacao() { return dataReligacao; }
    public void setDataReligacao(LocalDate dataReligacao) { this.dataReligacao = dataReligacao; }

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }

    public StatusCorte getStatus() { return status; }
    public void setStatus(StatusCorte status) { this.status = status; }

    public String getRegistadoPor() { return registadoPor; }
    public void setRegistadoPor(String registadoPor) { this.registadoPor = registadoPor; }

    public LocalDateTime getDataRegisto() { return dataRegisto; }
    public void setDataRegisto(LocalDateTime dataRegisto) { this.dataRegisto = dataRegisto; }
}

package com.aguasystem.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Regista cada evento de webhook do ZumboPay ja processado, pelo seu
 * event_id unico. Usado para IDEMPOTENCIA: se o ZumboPay reenviar o mesmo
 * evento (comum em sistemas de webhook, para garantir entrega), o sistema
 * ignora silenciosamente em vez de processar o pagamento duas vezes.
 */
@Entity
@Table(name = "webhook_evento_processado")
public class WebhookEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 100)
    private String eventId;

    @Column(name = "tipo_evento", length = 50)
    private String tipoEvento;

    @Column(name = "data_processamento", nullable = false)
    private LocalDateTime dataProcessamento;

    @PrePersist
    private void aoPersistir() {
        this.dataProcessamento = LocalDateTime.now();
    }

    public WebhookEvento() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getTipoEvento() { return tipoEvento; }
    public void setTipoEvento(String tipoEvento) { this.tipoEvento = tipoEvento; }

    public LocalDateTime getDataProcessamento() { return dataProcessamento; }
    public void setDataProcessamento(LocalDateTime dataProcessamento) { this.dataProcessamento = dataProcessamento; }
}

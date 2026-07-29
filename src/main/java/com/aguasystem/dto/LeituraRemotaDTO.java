package com.aguasystem.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

public class LeituraRemotaDTO {

    @NotNull(message = "O cliente e obrigatorio")
    private Long clienteId;

    @NotNull(message = "A leitura atual e obrigatoria")
    @DecimalMin(value = "0.00", message = "A leitura nao pode ser negativa")
    private BigDecimal leituraAtual;

    private MultipartFile foto;

    private BigDecimal latitude;

    private BigDecimal longitude;

    public Long getClienteId() { return clienteId; }
    public void setClienteId(Long clienteId) { this.clienteId = clienteId; }

    public BigDecimal getLeituraAtual() { return leituraAtual; }
    public void setLeituraAtual(BigDecimal leituraAtual) { this.leituraAtual = leituraAtual; }

    public MultipartFile getFoto() { return foto; }
    public void setFoto(MultipartFile foto) { this.foto = foto; }

    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }

    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }
}

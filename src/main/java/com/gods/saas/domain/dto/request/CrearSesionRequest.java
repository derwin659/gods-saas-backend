package com.gods.saas.domain.dto.request;

import lombok.Data;

@Data
public class CrearSesionRequest {

    // Identidad
    private Long tenantId;
    private Long sucursalId;

    // Cliente (opcional al inicio)
    private Long clienteId;      // puede ser null
    private String nombreCliente;

    // Servicio principal
    private String tipoServicio; // CORTE | TINTE | ONDULADO

    // Contexto inicial (opcional)
    private String observaciones;

    // Origen
    private String origen; // RECEPCION | BARBERO | APP | KIOSKO
}

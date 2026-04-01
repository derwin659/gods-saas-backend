package com.gods.saas.domain.dto.request;

import lombok.Data;

@Data
public class CrearSesionRequest {

    private Long tenantId;
    private Long sucursalId;

    private Long barberoId; // opcional

    private Long clienteId;
    private String nombreCliente;

    private String tipoServicio;
    private String observaciones;
    private String origen;
}
package com.gods.saas.domain.dto.response;

import lombok.Data;

@Data
public class SeleccionClienteResponse {

    private String sesionId;
    private String estado; // SELECCIONADA
    private String mensaje;
}


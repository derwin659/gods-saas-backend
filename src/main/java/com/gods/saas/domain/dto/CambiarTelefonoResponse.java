package com.gods.saas.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CambiarTelefonoResponse {
    private boolean cambiado;
    private String nuevoTelefono;
}


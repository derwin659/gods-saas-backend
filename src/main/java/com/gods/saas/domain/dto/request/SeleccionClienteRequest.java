package com.gods.saas.domain.dto.request;

import lombok.Data;

@Data
public class SeleccionClienteRequest {

    private CorteRequest corte;

    private TinteRequest tinte;       // null si no aplica

    private OnduladoRequest ondulado;  // null si no aplica
}


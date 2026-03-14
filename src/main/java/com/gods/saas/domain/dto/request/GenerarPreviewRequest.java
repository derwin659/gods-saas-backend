package com.gods.saas.domain.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GenerarPreviewRequest {

    @JsonProperty("imagen_base64")
    private String imagenBase64;

    private CorteRequest corte;

    private OpcionesRequest opciones;

    private ContextoRequest contexto;
}


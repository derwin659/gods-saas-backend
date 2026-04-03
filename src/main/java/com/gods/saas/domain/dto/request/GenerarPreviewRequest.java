package com.gods.saas.domain.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GenerarPreviewRequest {

    @JsonProperty("imagen_frontal_base64")
    private String imagenFrontalBase64;

    @JsonProperty("imagen_lateral_base64")
    private String imagenLateralBase64;

    @JsonProperty("imagen_trasera_base64")
    private String imagenTraseraBase64;

    private CorteRequest corte;

    private OpcionesRequest opciones;

    private ContextoRequest contexto;
}
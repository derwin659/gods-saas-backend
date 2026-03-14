package com.gods.saas.domain.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalizarImagenResponse {

    @JsonProperty("forma_rostro")
    private FormaRostroDto formaRostro;
    private CabelloDto cabello;


    private RecomendacionesDto recomendaciones;
    private MetaAnalisisDto meta;
}


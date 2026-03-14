package com.gods.saas.domain.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContextoRequest {

    @JsonProperty("forma_rostro")
    private String formaRostro;

    @JsonProperty("densidad_cabello")
    private String densidadCabello;
}


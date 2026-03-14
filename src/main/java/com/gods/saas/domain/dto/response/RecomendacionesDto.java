package com.gods.saas.domain.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecomendacionesDto {


    @JsonProperty("top_recomendado")
    private CorteRecomendadoDto topRecomendado;
    private List<CorteRecomendadoDto> cortes;
    private List<String> tintes;
}


package com.gods.saas.domain.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetaAnalisisDto {

    private String versionModelo;     // ia-analitica-v1
    private Long procesadoMs;
}


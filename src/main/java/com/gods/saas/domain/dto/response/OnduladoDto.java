package com.gods.saas.domain.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OnduladoDto {

    private Boolean apto;
    private String tipo;   // sin_largo | lacio | ondulado | rizado
}


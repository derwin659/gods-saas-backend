package com.gods.saas.domain.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FormaRostroDto {

    private String principal;     // cuadrado | redondo | ovalado | alargado
    private String alternativa;   // fallback
    private Double confianza;     // 0.0 - 1.0
}


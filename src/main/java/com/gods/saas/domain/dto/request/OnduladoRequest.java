package com.gods.saas.domain.dto.request;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OnduladoRequest {

    private Boolean aplicar;

    private String tipo; // ondulado | semi_ondulado
}


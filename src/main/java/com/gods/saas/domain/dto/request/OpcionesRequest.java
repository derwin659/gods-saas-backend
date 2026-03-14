package com.gods.saas.domain.dto.request;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OpcionesRequest {

    private OnduladoRequest ondulado;

    private TinteRequest tinte;
}


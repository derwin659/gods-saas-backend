package com.gods.saas.domain.dto.response;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UxAnalisisResponse {

    private FormaRostroUx formaRostro;

    private String mensaje;

    private List<CorteUx> cortesRecomendados;

    private boolean onduladoApto;

    private List<String> tintesSugeridos;
}


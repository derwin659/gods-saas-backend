package com.gods.saas.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RewardItemResponse {
    private Long id;
    private String titulo;
    private String descripcion;
    private int costoPuntos;
    private boolean destacado;
}

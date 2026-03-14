package com.gods.saas.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RedeemRewardResponse {
    private boolean success;
    private String message;
    private Integer puntosDisponibles;
    private String codigo;
    private Long redemptionId;
}
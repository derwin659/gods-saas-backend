package com.gods.saas.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RewardRedemptionDetailResponse {

    private Long redemptionId;
    private String codigo;
    private String estado;
    private Integer puntosUsados;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaUso;

    private Long customerId;
    private String customerNombreCompleto;
    private String customerTelefono;

    private Long rewardId;
    private String rewardNombre;
    private String rewardDescripcion;

    private Long usadoEnBranchId;
    private Long usadoPorUserId;
}

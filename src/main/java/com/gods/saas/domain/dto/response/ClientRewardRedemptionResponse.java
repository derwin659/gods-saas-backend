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
public class ClientRewardRedemptionResponse {

    private Long redemptionId;
    private String codigo;
    private String estado;
    private boolean usado;
    private boolean disponibleParaUsar;
    private Integer puntosUsados;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaUso;

    private Long rewardId;
    private String rewardNombre;
    private String rewardDescripcion;
    private String rewardImagenUrl;
}

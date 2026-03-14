package com.gods.saas.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UseRewardRedemptionResponse {
    private boolean success;
    private String message;
    private Long redemptionId;
    private String codigo;
    private String estado;
}

package com.gods.saas.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClientPointsResponse {
    private PointsSummaryResponse summary;
    private List<PointMovementResponse> movimientos;
    private List<RewardItemResponse> premios;
}

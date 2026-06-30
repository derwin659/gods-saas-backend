package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data @Builder
public class BarberServiceAssignmentResponse {
    private Long barberId;
    private Long branchId;
    private boolean configured;
    private List<Long> serviceIds;
}

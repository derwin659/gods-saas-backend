package com.gods.saas.domain.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StartWalkInAttendRequest {

    @NotNull
    private Long branchId;

    private Long customerId; // opcional

    @NotNull
    private Long serviceId;

    private String notas;
}
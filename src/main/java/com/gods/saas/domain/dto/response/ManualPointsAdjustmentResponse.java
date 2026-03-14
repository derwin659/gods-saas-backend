package com.gods.saas.domain.dto.response;

public record ManualPointsAdjustmentResponse(
        Long customerId,
        Integer previousPoints,
        Integer newPoints,
        Integer delta,
        String reason,
        String message
) {}

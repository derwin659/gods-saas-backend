package com.gods.saas.domain.dto.request;

public record ManualPointsAdjustmentRequest(
        Long customerId,
        Integer pointsDelta,
        String reason
) {}

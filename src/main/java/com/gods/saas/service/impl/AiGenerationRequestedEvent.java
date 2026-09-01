package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.GenerarImagenRequest;

public record AiGenerationRequestedEvent(
        String jobId,
        String sessionId,
        GenerarImagenRequest request
) {
}
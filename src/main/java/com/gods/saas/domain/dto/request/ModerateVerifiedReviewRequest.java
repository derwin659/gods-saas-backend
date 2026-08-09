package com.gods.saas.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ModerateVerifiedReviewRequest(
        @NotBlank(message = "Selecciona una decisión")
        String status,
        @NotBlank(message = "Explica la decisión")
        @Size(max = 500, message = "La nota no puede superar 500 caracteres")
        String note
) {}
package com.gods.saas.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReportVerifiedReviewRequest(
        @NotBlank(message = "Selecciona un motivo")
        @Size(max = 40)
        String reason,
        @Size(max = 500, message = "El detalle no puede superar 500 caracteres")
        String details
) {}
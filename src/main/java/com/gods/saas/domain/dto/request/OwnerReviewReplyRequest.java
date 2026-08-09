package com.gods.saas.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OwnerReviewReplyRequest(
        @NotBlank(message = "Escribe una respuesta")
        @Size(max = 500, message = "La respuesta no puede superar 500 caracteres")
        String reply
) {}
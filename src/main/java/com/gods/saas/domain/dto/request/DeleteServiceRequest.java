package com.gods.saas.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeleteServiceRequest(
        @NotBlank(message = "El motivo es obligatorio")
        @Size(min = 5, max = 500, message = "El motivo debe tener entre 5 y 500 caracteres")
        String reason
) {}

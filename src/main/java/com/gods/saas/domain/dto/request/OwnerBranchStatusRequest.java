package com.gods.saas.domain.dto.request;

import jakarta.validation.constraints.NotNull;

public record OwnerBranchStatusRequest(
        @NotNull(message = "El estado activo es obligatorio")
        Boolean activo
) {
}
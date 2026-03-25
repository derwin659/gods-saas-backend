package com.gods.saas.domain.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ServiceRequest(
        @NotBlank(message = "El nombre es obligatorio")
        String nombre,

        String descripcion,

        @NotNull(message = "La duración es obligatoria")
        @Min(value = 1, message = "La duración debe ser mayor a 0")
        Integer duracionMinutos,

        @NotNull(message = "El precio es obligatorio")
        @DecimalMin(value = "0.0", inclusive = true, message = "El precio no puede ser negativo")
        BigDecimal precio,

        String categoria,

        Boolean activo
) {
}
package com.gods.saas.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OwnerBranchUpsertRequest(

        @NotBlank(message = "El nombre de la sede es obligatorio")
        @Size(max = 150, message = "El nombre no puede exceder 150 caracteres")
        String nombre,

        @Size(max = 200, message = "La dirección no puede exceder 200 caracteres")
        String direccion,

        @Size(max = 30, message = "El teléfono no puede exceder 30 caracteres")
        String telefono,

        Boolean activo
) {
}
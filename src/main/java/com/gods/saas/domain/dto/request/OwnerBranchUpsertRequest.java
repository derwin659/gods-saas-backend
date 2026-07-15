package com.gods.saas.domain.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OwnerBranchUpsertRequest(

        @NotBlank(message = "El nombre de la sede es obligatorio")
        @Size(max = 150, message = "El nombre no puede exceder 150 caracteres")
        String nombre,

        @Size(max = 200, message = "La direccion no puede exceder 200 caracteres")
        String direccion,

        @Size(max = 30, message = "El telefono no puede exceder 30 caracteres")
        String telefono,

        Boolean activo,

        @Size(max = 120, message = "La ciudad no puede exceder 120 caracteres")
        String ciudad,

        @DecimalMin(value = "-90.0", message = "La latitud no es valida")
        @DecimalMax(value = "90.0", message = "La latitud no es valida")
        Double latitude,

        @DecimalMin(value = "-180.0", message = "La longitud no es valida")
        @DecimalMax(value = "180.0", message = "La longitud no es valida")
        Double longitude,

        Boolean publicVisible,

        Boolean directoryEnabled,

        @Size(max = 500, message = "La descripcion publica no puede exceder 500 caracteres")
        String publicDescription
) {
}
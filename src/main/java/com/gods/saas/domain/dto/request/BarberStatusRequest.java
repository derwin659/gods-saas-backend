package com.gods.saas.domain.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BarberStatusRequest {

    @NotNull
    private Boolean activo;
}

package com.gods.saas.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class QuickRegisterCustomerRequest {

    @NotNull
    private Long tenantId;

    @NotBlank
    private String telefono;

    @NotBlank
    private String nombres;

    private String apellidos;

    private String origenCliente;
}

package com.gods.saas.domain.dto.request;

import lombok.Data;

@Data
public class CreateQuickCustomerRequest {
    private String nombres;
    private String apellidos;
    private String telefono;
}
package com.gods.saas.domain.dto.request;

import lombok.Data;

@Data
public class ClientRegisterRequest {
    private Long tenantId;
    private String phone;
    private String nombres;
    private String apellidos;
}

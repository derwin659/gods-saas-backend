package com.gods.saas.domain.dto.request;

import lombok.Data;

@Data
public class ClientLoginRequest {
    private Long tenantId;
    private String phone;
    private String password;
    private String locale;
}
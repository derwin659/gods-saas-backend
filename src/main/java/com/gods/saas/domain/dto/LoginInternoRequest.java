package com.gods.saas.domain.dto;

import lombok.Data;

@Data
public class LoginInternoRequest {
    private String email;
    private String password;
    private Long tenantId;
}


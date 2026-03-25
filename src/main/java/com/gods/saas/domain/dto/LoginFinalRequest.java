package com.gods.saas.domain.dto;

import lombok.Data;

@Data
public class LoginFinalRequest {
    private Long userId;
    private Long tenantId;
    private String mode; // TENANT | SUPER_ADMIN
}


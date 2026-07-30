package com.gods.saas.domain.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginFinalResponse {
    private String token;

    private Long userId;
    private String nombre;
    private String email;

    private Long tenantId;
    private String tenantName;
    private String businessType;

    private Long branchId;
    private String branchName;

    private String role;
    private String locale;
    private String tenantLocale;
    private String timezone;
    private String currency;
    private String country;
}

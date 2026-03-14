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
    private Long branchId;
    private String branchName;
    private String role;
}


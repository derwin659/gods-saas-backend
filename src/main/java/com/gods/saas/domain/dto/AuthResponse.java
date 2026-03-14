package com.gods.saas.domain.dto;

import lombok.Data;

@Data
public class AuthResponse {
    private Long userId;
    private String nombre;
    private String phone;
    private Long tenantId;
    private String token; // JWT
}
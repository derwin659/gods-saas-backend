package com.gods.saas.domain.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TenantUserResponse {
    private Long id;
    private String nombre;
    private String email;
    private String role;
    private boolean active;
}


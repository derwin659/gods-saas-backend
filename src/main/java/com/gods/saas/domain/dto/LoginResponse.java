package com.gods.saas.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class LoginResponse {
    private Long userId;
    private String nombre;
    private List<TenantAccess> tenants;

    @Data
    public static class TenantAccess {
        private Long tenantId;
        private String tenantName;
        private String role;

        public TenantAccess(Long tenantId, String tenantName, String role) {
            this.tenantId = tenantId;
            this.tenantName = tenantName;
            this.role = role;
        }
    }

    public LoginResponse(Long userId, String nombre, List<TenantAccess> tenants) {
        this.userId = userId;
        this.nombre = nombre;
        this.tenants = tenants;
    }
}


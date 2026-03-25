package com.gods.saas.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private Long userId;
    private String nombre;
    private String globalRole;
    private List<TenantAccess> tenants;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TenantAccess {
        private Long tenantId;
        private String tenantName;
        private String role;
    }
}
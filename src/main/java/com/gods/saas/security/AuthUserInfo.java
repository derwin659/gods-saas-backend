package com.gods.saas.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthUserInfo {
    private Long userId;
    private Long tenantId;
    private Long branchId;
    private String role;
    private String username;
}

package com.gods.saas.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AuthHelper {

    public AuthUserInfo getUserInfo(Authentication authentication) {
        Object principal = authentication.getPrincipal();

        if (principal instanceof Map<?, ?> claims) {
            return AuthUserInfo.builder()
                    .userId(toLong(claims.get("userId")))
                    .tenantId(toLong(claims.get("tenantId")))
                    .branchId(toLong(claims.get("branchId")))
                    .role(toStringValue(claims.get("role")))
                    .username(toStringValue(claims.get("sub")))
                    .build();
        }

        return AuthUserInfo.builder()
                .username(authentication.getName())
                .build();
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private String toStringValue(Object value) {
        return value == null ? null : value.toString();
    }
}

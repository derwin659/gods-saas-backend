package com.gods.saas.security;

import com.gods.saas.domain.model.AppUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

public class SecurityUtils {

    public static Long getCurrentTenantId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null) {
            throw new RuntimeException("No hay autenticación disponible");
        }

        // 1) Intentar desde details del JWT
        Object details = authentication.getDetails();
        if (details instanceof Map<?, ?> map) {
            Object tenantId = map.get("tenantId");
            if (tenantId != null) {
                return toLong(tenantId);
            }
        }

        // 2) Intentar desde principal si fuera un AppUser
        Object principal = authentication.getPrincipal();
        if (principal instanceof AppUser appUser) {
            if (appUser.getTenant() != null && appUser.getTenant().getId() != null) {
                return appUser.getTenant().getId();
            }
        }

        // 3) Intentar desde TenantContext
        Long tenantIdFromContext = TenantContext.getTenantId();
        if (tenantIdFromContext != null) {
            return tenantIdFromContext;
        }

        throw new RuntimeException("No se pudo obtener tenantId del usuario autenticado");
    }

    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null) {
            throw new RuntimeException("No hay autenticación disponible");
        }

        Object details = authentication.getDetails();
        if (details instanceof Map<?, ?> map) {
            Object userId = map.get("userId");
            if (userId != null) {
                return toLong(userId);
            }
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof AppUser appUser) {
            return appUser.getId();
        }

        throw new RuntimeException("No se pudo obtener userId del usuario autenticado");
    }

    private static Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Long l) return l;
        if (value instanceof Integer i) return i.longValue();
        if (value instanceof Number n) return n.longValue();
        return Long.parseLong(value.toString());
    }
}
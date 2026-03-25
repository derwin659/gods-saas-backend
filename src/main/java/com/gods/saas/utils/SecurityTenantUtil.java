package com.gods.saas.utils;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SecurityTenantUtil {

    public Long getCurrentTenantId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null) {
            throw new IllegalStateException("No hay autenticación");
        }

        Object details = auth.getDetails();

        if (details instanceof Map<?, ?> map) {
            Object tenantId = map.get("tenantId");

            if (tenantId instanceof Number num) {
                return num.longValue();
            }
        }

        throw new IllegalStateException("No se pudo obtener tenantId del token");
    }
}
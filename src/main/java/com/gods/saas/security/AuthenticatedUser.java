package com.gods.saas.security;

import com.gods.saas.domain.model.AppUser;
import com.gods.saas.domain.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class AuthenticatedUser {

    private final AppUserRepository appUserRepository;

    public AppUser getRequiredUser() {
        Long userId = getUserId();
        return appUserRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario autenticado no encontrado"));
    }

    public Long getUserId() {
        Object details = getAuthentication().getDetails();
        if (details instanceof Map<?, ?> map) {
            Object value = map.get("userId");
            if (value instanceof Number n) return n.longValue();
            if (value instanceof String s) return Long.parseLong(s);
        }
        throw new RuntimeException("No se encontró userId en la sesión");
    }

    public Long getTenantId() {
        Object details = getAuthentication().getDetails();
        if (details instanceof Map<?, ?> map) {
            Object value = map.get("tenantId");
            if (value instanceof Number n) return n.longValue();
            if (value instanceof String s) return Long.parseLong(s);
        }
        throw new RuntimeException("No se encontró tenantId en la sesión");
    }

    public Long getBranchId() {
        Object details = getAuthentication().getDetails();
        if (details instanceof Map<?, ?> map) {
            Object value = map.get("branchId");
            if (value instanceof Number n) return n.longValue();
            if (value instanceof String s) return Long.parseLong(s);
        }
        throw new RuntimeException("No se encontró branchId en la sesión");
    }

    private Authentication getAuthentication() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new RuntimeException("No hay autenticación en el contexto");
        }
        return auth;
    }
}

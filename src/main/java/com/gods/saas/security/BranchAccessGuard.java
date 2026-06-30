package com.gods.saas.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class BranchAccessGuard {

    public Long resolve(Long requestedBranchId, Long sessionBranchId) {
        Long effectiveBranchId = requestedBranchId != null ? requestedBranchId : sessionBranchId;
        if (effectiveBranchId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se encontró una sede operativa en la sesión");
        }

        if (requestedBranchId != null
                && !requestedBranchId.equals(sessionBranchId)
                && !isOwner()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes acceso a la sede solicitada");
        }
        return effectiveBranchId;
    }

    public Long resolveOptionalForOwner(Long requestedBranchId, Long sessionBranchId) {
        return isOwner() ? requestedBranchId : resolve(requestedBranchId, sessionBranchId);
    }

    private boolean isOwner() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_OWNER".equals(authority.getAuthority()));
    }
}

package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.UpdateAdminPermissionsRequest;
import com.gods.saas.domain.dto.response.AdminPermissionsBundleResponse;
import com.gods.saas.domain.enums.AdminPermissionKey;
import com.gods.saas.domain.model.AdminPermission;
import com.gods.saas.domain.model.AppUser;
import com.gods.saas.domain.model.Tenant;
import com.gods.saas.domain.repository.AdminPermissionRepository;
import com.gods.saas.domain.repository.AppUserRepository;
import com.gods.saas.security.TenantContext;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminPermissionService {

    private final AdminPermissionRepository adminPermissionRepository;
    private final AppUserRepository appUserRepository;

    public AdminPermissionsBundleResponse getMyPermissions() {
        Long tenantId = TenantContext.getTenantId();
        Long userId = getAuthenticatedUserId();
        String role = getAuthenticatedRole();

        boolean owner = "OWNER".equalsIgnoreCase(role);

        if (owner) {
            return AdminPermissionsBundleResponse.builder()
                    .tenantId(tenantId)
                    .userId(userId)
                    .role(role)
                    .owner(true)
                    .permissions(allPermissionKeys())
                    .build();
        }

        List<String> permissions = adminPermissionRepository
                .findByTenant_IdAndUser_IdOrderByPermissionKeyAsc(tenantId, userId)
                .stream()
                .filter(p -> Boolean.TRUE.equals(p.getEnabled()))
                .map(AdminPermission::getPermissionKey)
                .toList();

        return AdminPermissionsBundleResponse.builder()
                .tenantId(tenantId)
                .userId(userId)
                .role(role)
                .owner(false)
                .permissions(permissions)
                .build();
    }

    public AdminPermissionsBundleResponse getPermissionsForAdmin(Long adminUserId) {
        validarOwner();

        Long tenantId = TenantContext.getTenantId();

        AppUser admin = appUserRepository.findByIdAndTenantId(adminUserId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Administrador no encontrado"));

        if (!"ADMIN".equalsIgnoreCase(admin.getRol())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El usuario indicado no es ADMIN");
        }

        List<String> permissions = adminPermissionRepository
                .findByTenant_IdAndUser_IdOrderByPermissionKeyAsc(tenantId, adminUserId)
                .stream()
                .filter(p -> Boolean.TRUE.equals(p.getEnabled()))
                .map(AdminPermission::getPermissionKey)
                .toList();

        return AdminPermissionsBundleResponse.builder()
                .tenantId(tenantId)
                .userId(adminUserId)
                .role("ADMIN")
                .owner(false)
                .permissions(permissions)
                .build();
    }

    @Transactional
    public AdminPermissionsBundleResponse updatePermissionsForAdmin(
            Long adminUserId,
            UpdateAdminPermissionsRequest request
    ) {
        validarOwner();

        Long tenantId = TenantContext.getTenantId();

        AppUser admin = appUserRepository.findByIdAndTenantId(adminUserId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Administrador no encontrado"
                ));

        if (!"ADMIN".equalsIgnoreCase(admin.getRol())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Solo puedes asignar permisos a usuarios ADMIN"
            );
        }

        Set<String> cleanPermissions = new LinkedHashSet<>();

        if (request != null && request.getPermissions() != null) {
            for (String raw : request.getPermissions()) {
                String key = normalizePermission(raw);

                if (!AdminPermissionKey.isValid(key)) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Permiso inválido: " + raw
                    );
                }

                cleanPermissions.add(key);
            }
        }

        // Si el dueño activa cualquier permiso interno de configuración,
        // activamos también CONFIG_ACCESS para que aparezca la pestaña Config.
        boolean hasAnyConfigChild = cleanPermissions.stream()
                .anyMatch(p -> p.startsWith("CONFIG_") && !"CONFIG_ACCESS".equals(p));

        if (hasAnyConfigChild) {
            cleanPermissions.add("CONFIG_ACCESS");
        }

        // Traemos todos los permisos existentes de ese admin.
        List<AdminPermission> existingPermissions =
                adminPermissionRepository.findByTenant_IdAndUser_IdOrderByPermissionKeyAsc(
                        tenantId,
                        adminUserId
                );

        // Primero desactivamos todos.
        for (AdminPermission permission : existingPermissions) {
            permission.setEnabled(false);
            permission.setUpdatedAt(LocalDateTime.now());
            adminPermissionRepository.save(permission);
        }

        // Luego activamos o creamos los seleccionados.
        for (String key : cleanPermissions) {
            AdminPermission permission = existingPermissions.stream()
                    .filter(p -> key.equalsIgnoreCase(p.getPermissionKey()))
                    .findFirst()
                    .orElse(null);

            if (permission == null) {
                permission = AdminPermission.builder()
                        .tenant(new Tenant(tenantId))
                        .branch(admin.getBranch())
                        .user(admin)
                        .permissionKey(key)
                        .enabled(true)
                        .createdAt(LocalDateTime.now())
                        .build();
            } else {
                permission.setEnabled(true);
                permission.setBranch(admin.getBranch());
                permission.setUpdatedAt(LocalDateTime.now());
            }

            adminPermissionRepository.save(permission);
        }

        return getPermissionsForAdmin(adminUserId);
    }

    @Transactional
    public void createDefaultPermissionsForNewAdmin(Long tenantId, Long adminUserId) {
        AppUser admin = appUserRepository.findByIdAndTenantId(adminUserId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Administrador no encontrado"));

        for (String key : AdminPermissionKey.defaultsForNewAdmin()) {
            boolean exists = adminPermissionRepository
                    .findByTenant_IdAndUser_IdAndPermissionKey(tenantId, adminUserId, key)
                    .isPresent();

            if (exists) continue;

            AdminPermission permission = AdminPermission.builder()
                    .tenant(new Tenant(tenantId))
                    .branch(admin.getBranch())
                    .user(admin)
                    .permissionKey(key)
                    .enabled(true)
                    .createdAt(LocalDateTime.now())
                    .build();

            adminPermissionRepository.save(permission);
        }
    }

    public boolean hasPermission(Long tenantId, Long userId, String permissionKey) {
        String role = getAuthenticatedRole();

        if ("OWNER".equalsIgnoreCase(role)) return true;

        if (!"ADMIN".equalsIgnoreCase(role)) return false;

        return adminPermissionRepository.existsByTenant_IdAndUser_IdAndPermissionKeyAndEnabledTrue(
                tenantId,
                userId,
                normalizePermission(permissionKey)
        );
    }

    public void checkPermission(String permissionKey) {
        Long tenantId = TenantContext.getTenantId();
        Long userId = getAuthenticatedUserId();
        String role = getAuthenticatedRole();

        if ("OWNER".equalsIgnoreCase(role)) return;

        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new AccessDeniedException("No tienes permiso para esta acción");
        }

        boolean allowed = adminPermissionRepository.existsByTenant_IdAndUser_IdAndPermissionKeyAndEnabledTrue(
                tenantId,
                userId,
                normalizePermission(permissionKey)
        );

        if (!allowed) {
            throw new AccessDeniedException("No tienes permiso para esta acción");
        }
    }

    private void validarOwner() {
        String role = getAuthenticatedRole();
        if (!"OWNER".equalsIgnoreCase(role)) {
            throw new AccessDeniedException("Solo el dueño puede administrar permisos");
        }
    }

    private Long getAuthenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || auth.getPrincipal() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión no válida");
        }

        Object principal = auth.getPrincipal();

        if (principal instanceof Number n) {
            return n.longValue();
        }

        if (principal instanceof AppUser user) {
            return user.getId();
        }

        try {
            return Long.parseLong(principal.toString());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No se pudo identificar al usuario");
        }
    }

    private String getAuthenticatedRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || auth.getAuthorities() == null) {
            return "";
        }

        return auth.getAuthorities()
                .stream()
                .findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .orElse("");
    }

    private String normalizePermission(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    }

    private List<String> allPermissionKeys() {
        return List.of(AdminPermissionKey.values())
                .stream()
                .map(Enum::name)
                .toList();
    }
}
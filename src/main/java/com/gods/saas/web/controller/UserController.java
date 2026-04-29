package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.*;
import com.gods.saas.domain.model.AppUser;
import com.gods.saas.domain.model.Branch;
import com.gods.saas.domain.model.RoleType;
import com.gods.saas.domain.model.UserTenantRole;
import com.gods.saas.domain.repository.AppUserRepository;
import com.gods.saas.domain.repository.BranchRepository;
import com.gods.saas.domain.repository.UserTenantRoleRepository;
import com.gods.saas.service.impl.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/api/internal/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AppUserRepository appUserRepository;
    private final BranchRepository branchRepository;
    private final UserTenantRoleRepository userTenantRoleRepository;

    // =====================================================
    // LISTAR USUARIOS
    // =====================================================
    @GetMapping
    public List<AppUserResponse> getAll() {
        return userService.getUsers();
    }

    // =====================================================
    // OBTENER USUARIO POR ID
    // =====================================================
    @GetMapping("/{id}")
    public ResponseEntity<AppUserResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUser(id));
    }

    // =====================================================
    // CREAR USUARIO INTERNO
    // =====================================================
    @PostMapping
    public ResponseEntity<AppUserResponse> crear(@RequestBody CrearUsuarioRequest req) {
        return ResponseEntity.ok(userService.crearUsuario(req));
    }

    // =====================================================
    // ACTUALIZAR USUARIO
    // =====================================================
    @PutMapping("/{id}")
    public ResponseEntity<AppUserResponse> actualizar(
            @PathVariable Long id,
            @RequestBody ActualizarUsuarioInternoRequest req) {

        return ResponseEntity.ok(
                AppUserResponse.from(
                        userService.actualizarUsuario(
                                id,
                                req.getNombre(),
                                req.getApellido(),
                                req.getPhone(),
                                req.getBranchId(),
                                req.getRol()
                        )
                )
        );
    }

    // =====================================================
    // CAMBIAR ROL ADMIN <-> BARBER
    // Usado por Flutter:
    // PUT /api/internal/users/{id}/role
    // body: { "targetRole": "BARBER", "branchId": 6 }
    // =====================================================
    @PutMapping("/{id}/role")
    public ResponseEntity<AppUserResponse> cambiarRol(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            Authentication authentication
    ) {
        AppUser targetUser = appUserRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (targetUser.getTenant() == null || targetUser.getTenant().getId() == null) {
            throw new RuntimeException("El usuario no tiene tenant asignado.");
        }

        Long tenantId = targetUser.getTenant().getId();
        Long actorUserId = extractUserId(authentication);

        boolean isOwner = userTenantRoleRepository.existsByUserIdAndTenantIdAndRoleIn(
                actorUserId,
                tenantId,
                List.of(RoleType.OWNER)
        );

        if (!isOwner) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Solo el dueño puede cambiar roles."
            );
        }

        String targetRoleRaw = body.get("targetRole") == null
                ? ""
                : body.get("targetRole").toString().trim().toUpperCase();

        if (!"ADMIN".equals(targetRoleRaw) && !"BARBER".equals(targetRoleRaw)) {
            throw new RuntimeException("Rol inválido. Solo se permite ADMIN o BARBER.");
        }

        Object branchValue = body.get("branchId");
        if (branchValue == null) {
            throw new RuntimeException("branchId es obligatorio.");
        }

        Long branchId = parseLong(branchValue, "branchId");

        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Sede no encontrada."));

        if (branch.getTenant() == null || !tenantId.equals(branch.getTenant().getId())) {
            throw new RuntimeException("La sede no pertenece al tenant del usuario.");
        }

        RoleType targetRole = RoleType.valueOf(targetRoleRaw);

        UserTenantRole userTenantRole = userTenantRoleRepository
                .findByUser_IdAndTenant_Id(targetUser.getId(), tenantId)
                .orElseThrow(() -> new RuntimeException("El usuario no tiene rol asignado en este tenant."));

        userTenantRole.setRole(targetRole);
        userTenantRole.setBranch(branch);
        userTenantRoleRepository.save(userTenantRole);

        // Mantener sincronizado app_user para listados antiguos y compatibilidad.
        targetUser.setRol(targetRoleRaw);
        targetUser.setBranch(branch);
        AppUser saved = appUserRepository.save(targetUser);

        return ResponseEntity.ok(AppUserResponse.from(saved));
    }

    // =====================================================
    // CAMBIAR PASSWORD
    // =====================================================
    @PutMapping("/{id}/password")
    public ResponseEntity<String> cambiarPassword(
            @PathVariable Long id,
            @RequestBody CambiarPasswordRequest req) {

        userService.cambiarPassword(
                id,
                userService.hashPassword(req.getNewPassword())
        );
        return ResponseEntity.ok("Password actualizado correctamente");
    }

    // =====================================================
    // CAMBIAR ESTADO
    // =====================================================
    @PutMapping("/{id}/estado")
    public ResponseEntity<String> cambiarEstado(
            @PathVariable Long id,
            @RequestBody CambiarEstadoRequest req) {

        userService.cambiarEstado(id, req.isActivo());
        return ResponseEntity.ok("Estado actualizado");
    }

    private Long extractUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new RuntimeException("Sesión no válida");
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof Number n) {
            return n.longValue();
        }

        if (principal instanceof AppUser user) {
            return user.getId();
        }

        try {
            return Long.parseLong(principal.toString());
        } catch (Exception e) {
            throw new RuntimeException("No se pudo identificar al usuario autenticado.");
        }
    }

    private Long parseLong(Object value, String fieldName) {
        if (value instanceof Number n) {
            return n.longValue();
        }

        try {
            return Long.parseLong(value.toString());
        } catch (Exception e) {
            throw new RuntimeException(fieldName + " inválido.");
        }
    }
}

package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.AppUserResponse;
import com.gods.saas.domain.dto.CrearUsuarioRequest;
import com.gods.saas.domain.dto.request.BootstrapOwnerRequest;
import com.gods.saas.domain.dto.request.ChangePasswordRequest;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.AppUserRepository;
import com.gods.saas.domain.repository.BranchRepository;
import com.gods.saas.domain.repository.TenantRepository;
import com.gods.saas.domain.repository.UserTenantRoleRepository;
import com.gods.saas.security.TenantContext;
import com.gods.saas.service.impl.impl.SubscriptionService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final PasswordEncoder passwordEncoder;
    private final AppUserRepository userRepository;
    private final UserTenantRoleRepository userTenantRoleRepository;
    private final TenantRepository tenantRepository;
    private final BranchRepository branchRepository;
    private final SubscriptionService subscriptionService;

    private void validarOwner() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_OWNER"))) {
            throw new AccessDeniedException("Solo el dueño puede realizar esta acción");
        }
    }

    public void changeMyPassword(Long userId, ChangePasswordRequest req) {
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solicitud inválida");
        }

        String currentPassword = req.getCurrentPassword() == null ? "" : req.getCurrentPassword().trim();
        String newPassword = req.getNewPassword() == null ? "" : req.getNewPassword().trim();
        String confirmPassword = req.getConfirmPassword() == null ? "" : req.getConfirmPassword().trim();

        if (currentPassword.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña actual es obligatoria");
        if (newPassword.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La nueva contraseña es obligatoria");
        if (confirmPassword.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debes confirmar la nueva contraseña");
        if (!newPassword.equals(confirmPassword)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La nueva contraseña y su confirmación no coinciden");
        if (newPassword.length() < 6) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La nueva contraseña debe tener al menos 6 caracteres");

        AppUser user = getById(userId);
        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El usuario no tiene contraseña configurada");
        }
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "La contraseña actual es incorrecta");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La nueva contraseña no puede ser igual a la actual");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setFechaActualizacion(LocalDateTime.now());
        userRepository.save(user);
    }

    @Transactional
    public void deleteMyInternalAccount(Long userId, String currentPassword, String confirmation) {
        if (userId == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Usuario inválido");

        final String confirm = confirmation == null ? "" : confirmation.trim().toUpperCase();
        if (!Objects.equals(confirm, "ELIMINAR")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debes confirmar la eliminación escribiendo ELIMINAR");
        }

        AppUser user = userRepository.findByIdWithTenant(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        if (!Boolean.TRUE.equals(user.getActivo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cuenta ya fue eliminada");
        }

        String role = user.getRol() == null ? "" : user.getRol().trim().toUpperCase(Locale.ROOT);
        if ("OWNER".equals(role)) {
            long ownersActivos = userRepository.countActiveByTenantIdAndRoles(user.getTenant().getId(), List.of("OWNER"));
            if (ownersActivos <= 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "No puedes eliminar la única cuenta OWNER activa de la barbería");
            }
        }

        String rawCurrentPassword = currentPassword == null ? "" : currentPassword.trim();
        if (rawCurrentPassword.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña actual es obligatoria");
        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El usuario no tiene contraseña configurada");
        }
        if (!passwordEncoder.matches(rawCurrentPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "La contraseña actual es incorrecta");
        }

        String marker = "deleted_user_" + user.getId() + "_" + System.currentTimeMillis();
        user.setNombre("Cuenta eliminada");
        user.setApellido("");
        user.setEmail(marker + "@deleted.local");
        user.setPhone(marker);
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setPhotoUrl(null);
        user.setActivo(false);
        user.setBranch(null);
        user.setFechaActualizacion(LocalDateTime.now());
        userRepository.save(user);
    }

    public String hashPassword(String rawPassword) {
        if (rawPassword == null || rawPassword.trim().length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña debe tener al menos 6 caracteres");
        }
        return passwordEncoder.encode(rawPassword.trim());
    }

    public AppUser crearUsuarioInterno(String nombre, String apellido, String email, String phone, String rol, Long branchId, String passwordHash) {
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("El correo ya está registrado en este tenant");
        }

        AppUser user = AppUser.builder()
                .branch(branchId != null ? new Branch(branchId) : null)
                .nombre(nombre)
                .apellido(apellido)
                .email(email)
                .phone(phone)
                .rol(rol)
                .passwordHash(passwordHash)
                .activo(true)
                .fechaCreacion(LocalDateTime.now())
                .build();

        return userRepository.save(user);
    }

    public AppUser loginInterno(String email, Long tenantId, String rawPassword) {
        AppUser user = userRepository.findByEmailAndTenantId(email, tenantId)
                .orElseThrow(() -> new RuntimeException("Credenciales inválidas"));
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new RuntimeException("Credenciales inválidas");
        }
        return user;
    }

    public AppUser getById(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    public List<AppUserResponse> getUsers() {
        validarOwner();
        Long tenantId = TenantContext.getTenantId();
        return userRepository.findByTenantId(tenantId).stream().map(AppUserResponse::from).toList();
    }

    public AppUserResponse getUser(Long id) {
        validarOwner();
        Long tenantId = TenantContext.getTenantId();
        AppUser user = userRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        return AppUserResponse.from(user);
    }

    @Transactional
    public AppUser actualizarUsuario(Long userId, String nombre, String apellido, String phone, Long branchId, String rol) {
        validarOwner();
        Long tenantId = TenantContext.getTenantId();
        AppUser user = userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        String newRole = rol == null ? user.getRol() : normalizeInternalRole(rol);
        if ("OWNER".equalsIgnoreCase(user.getRol()) && !"OWNER".equals(newRole)) {
            long ownersActivos = userRepository.countActiveByTenantIdAndRoles(tenantId, List.of("OWNER"));
            if (ownersActivos <= 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "No puedes quitar el rol OWNER al único dueño activo");
            }
        }

        if (nombre != null) user.setNombre(nombre.trim());
        if (apellido != null) user.setApellido(apellido.trim());
        if (phone != null) user.setPhone(phone.trim());
        user.setRol(newRole);

        Branch branch = null;
        if (branchId != null) {
            branch = branchRepository.findByIdAndTenant_Id(branchId, tenantId)
                    .orElseThrow(() -> new RuntimeException("La sede no pertenece al tenant"));
            user.setBranch(branch);
        }

        user.setFechaActualizacion(LocalDateTime.now());
        AppUser saved = userRepository.save(user);

        UserTenantRole utr = userTenantRoleRepository.findByUser_IdAndTenant_Id(userId, tenantId).orElse(null);
        if (utr != null) {
            utr.setRole(RoleType.valueOf(newRole));
            if (branchId != null) utr.setBranch(branch);
            userTenantRoleRepository.save(utr);
        }

        return saved;
    }

    public void cambiarPassword(Long userId, String newPasswordHash) {
        validarOwner();
        Long tenantId = TenantContext.getTenantId();
        AppUser user = userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        user.setPasswordHash(newPasswordHash);
        user.setFechaActualizacion(LocalDateTime.now());
        userRepository.save(user);
    }

    public void cambiarEstado(Long userId, boolean activo) {
        validarOwner();
        Long tenantId = TenantContext.getTenantId();
        AppUser user = userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        String role = user.getRol() == null ? "" : user.getRol().trim().toUpperCase(Locale.ROOT);
        if (!activo && "OWNER".equals(role)) {
            long ownersActivos = userRepository.countActiveByTenantIdAndRoles(tenantId, List.of("OWNER"));
            if (ownersActivos <= 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "No puedes desactivar el único OWNER activo de la barbería");
            }
        }

        user.setActivo(activo);
        user.setFechaActualizacion(LocalDateTime.now());
        userRepository.save(user);
    }

    @Transactional
    public AppUserResponse crearUsuario(CrearUsuarioRequest req) {
        Long tenantId = TenantContext.getTenantId();
        String role = normalizeInternalRole(req.getRol());

        if (userRepository.countByTenantId(tenantId) == 0) {
            if (!"OWNER".equals(role)) {
                throw new RuntimeException("El primer usuario del tenant debe ser OWNER");
            }
        } else {
            validarOwner();
            subscriptionService.validateSubscriptionActive(tenantId);
            if ("BARBER".equals(role)) {
                subscriptionService.validateBarberLimit(tenantId);
            }
            if ("ADMIN".equals(role) || "OWNER".equals(role)) {
                subscriptionService.validateAdminLimit(tenantId);
            }
        }

        String email = required(req.getEmail(), "El email es obligatorio").toLowerCase(Locale.ROOT);
        String password = required(req.getPassword(), "La contraseña es obligatoria");
        if (password.length() < 6) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña debe tener al menos 6 caracteres");
        if (userRepository.existsByEmailAndTenantId(email, tenantId)) {
            throw new RuntimeException("Email ya existe en esta barbería");
        }

        AppUser user = AppUser.builder()
                .nombre(required(req.getNombre(), "El nombre es obligatorio"))
                .apellido(req.getApellido() == null ? "" : req.getApellido().trim())
                .email(email)
                .phone(req.getPhone() == null ? null : req.getPhone().trim())
                .passwordHash(passwordEncoder.encode(password))
                .activo(true)
                .rol(role)
                .tenant(new Tenant(tenantId))
                .fechaCreacion(LocalDateTime.now())
                .build();

        if (req.getBranchId() != null) {
            Branch branch = branchRepository.findByIdAndTenant_Id(req.getBranchId(), tenantId)
                    .orElseThrow(() -> new RuntimeException("La sede no pertenece al tenant"));
            user.setBranch(branch);
        }

        userRepository.save(user);

        UserTenantRole userTenantRole = UserTenantRole.builder()
                .user(user)
                .tenant(new Tenant(tenantId))
                .branch(user.getBranch())
                .role(RoleType.valueOf(role))
                .build();
        userTenantRoleRepository.save(userTenantRole);

        return AppUserResponse.from(user);
    }

    @Transactional
    public AppUserResponse bootstrapRegister(BootstrapOwnerRequest req) {
        Tenant tenant = Tenant.builder()
                .nombre(req.getTenantNombre())
                .ownerName(req.getOwnerName())
                .pais(req.getPais())
                .ciudad(req.getCiudad())
                .plan("STARTER")
                .active(true)
                .estadoSuscripcion("TRIAL")
                .fechaCreacion(LocalDateTime.now())
                .build();

        tenant = tenantRepository.save(tenant);
        subscriptionService.createStarterTrial(tenant.getId());

        Branch branch = Branch.builder()
                .tenant(tenant)
                .nombre(req.getBranchNombre() != null ? req.getBranchNombre() : "Sede Principal")
                .direccion(req.getBranchDireccion())
                .activo(true)
                .fechaCreacion(LocalDateTime.now())
                .build();

        branch = branchRepository.save(branch);

        if (userRepository.existsByEmailAndTenantId(req.getEmail(), tenant.getId())) {
            throw new RuntimeException("Email ya existe en este tenant");
        }

        AppUser user = AppUser.builder()
                .nombre(req.getNombre())
                .apellido(req.getApellido())
                .email(req.getEmail())
                .phone(req.getPhone())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .rol("OWNER")
                .activo(true)
                .tenant(tenant)
                .branch(branch)
                .fechaCreacion(LocalDateTime.now())
                .build();

        user = userRepository.save(user);

        UserTenantRole ownerRole = UserTenantRole.builder()
                .user(user)
                .tenant(tenant)
                .branch(branch)
                .role(RoleType.OWNER)
                .build();
        userTenantRoleRepository.save(ownerRole);

        return AppUserResponse.from(user);
    }

    private String normalizeInternalRole(String raw) {
        String role = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (!List.of("OWNER", "ADMIN", "BARBER").contains(role)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rol no permitido. Usa OWNER, ADMIN o BARBER");
        }
        return role;
    }

    private String required(String value, String message) {
        String v = value == null ? "" : value.trim();
        if (v.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        return v;
    }
}

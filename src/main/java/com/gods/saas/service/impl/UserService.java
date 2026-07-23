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
import java.util.ArrayList;
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
    private final AdminPermissionService adminPermissionService;

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
        return userRepository.findByTenantId(tenantId).stream().map(user -> responseWithBranches(user, tenantId)).toList();
    }

    public AppUserResponse getUser(Long id) {
        validarOwner();
        Long tenantId = TenantContext.getTenantId();
        AppUser user = userRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        return responseWithBranches(user, tenantId);
    }

    @Transactional
    public AppUser actualizarUsuario(Long userId, String nombre, String apellido, String phone, Long branchId, String rol) {
        return actualizarUsuario(userId, nombre, apellido, phone, branchId, rol, false, null, null);
    }

    @Transactional
    public AppUser actualizarUsuario(Long userId, String nombre, String apellido, String phone, Long branchId, String rol, boolean preserveProfessionalProfile, List<Long> professionalBranchIds, Boolean canSell) {
        return actualizarUsuario(userId, nombre, apellido, phone, branchId, rol,
                preserveProfessionalProfile ? Boolean.TRUE : null,
                professionalBranchIds,
                canSell);
    }

    @Transactional
    public AppUser actualizarUsuario(Long userId, String nombre, String apellido, String phone, Long branchId, String rol, Boolean professionalProfileEnabled, List<Long> professionalBranchIds, Boolean canSell) {
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

        boolean hasProfessionalProfile = userTenantRoleRepository.existsByUser_IdAndTenant_IdAndRole(user.getId(), tenantId, RoleType.BARBER);
        boolean supportsProfessionalProfile = "ADMIN".equals(newRole) || "CASHIER".equals(newRole);
        boolean enableProfessionalProfile = Boolean.TRUE.equals(professionalProfileEnabled)
                || (professionalProfileEnabled == null && hasProfessionalProfile && supportsProfessionalProfile);
        boolean disableProfessionalProfile = Boolean.FALSE.equals(professionalProfileEnabled);

        if (nombre != null) user.setNombre(nombre.trim());
        if (apellido != null) user.setApellido(apellido.trim());
        if (phone != null) user.setPhone(phone.trim());
        user.setRol(newRole);
        if (professionalProfileEnabled != null || canSell != null) {
            user.setCanSell(canSell == null ? enableProfessionalProfile : canSell);
        }

        Branch branch = null;
        if (branchId != null) {
            branch = branchRepository.findByIdAndTenant_Id(branchId, tenantId)
                    .orElseThrow(() -> new RuntimeException("La sede no pertenece al tenant"));
            user.setBranch(branch);
        }

        user.setFechaActualizacion(LocalDateTime.now());
        AppUser saved = userRepository.save(user);

        if (enableProfessionalProfile && supportsProfessionalProfile) {
            ensureProfessionalProfileRoles(saved, tenantId, professionalBranchIds, branch);
            ensureUserRoleForBranch(saved, tenantId, RoleType.valueOf(newRole), branch);
        } else if (disableProfessionalProfile) {
            removeProfessionalProfileRoles(saved.getId(), tenantId);
            ensureUserRoleForBranch(saved, tenantId, RoleType.valueOf(newRole), branch);
        } else {
            UserTenantRole utr = userTenantRoleRepository.findFirstByUser_IdAndTenant_IdOrderByIdAsc(userId, tenantId).orElse(null);
            if (utr != null) {
                utr.setRole(RoleType.valueOf(newRole));
                if (branchId != null) utr.setBranch(branch);
                userTenantRoleRepository.save(utr);
            }
        }

        return saved;
    }

    @Transactional
    public AppUserResponse updateUserBranches(Long userId, List<Long> branchIds) {
        validarOwner();
        Long tenantId = TenantContext.getTenantId();
        AppUser user = userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        if (branchIds == null || branchIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona al menos una sede");
        }
        List<Long> cleanIds = branchIds.stream().filter(Objects::nonNull).distinct().toList();
        List<Branch> branches = branchRepository.findAllById(cleanIds).stream()
                .filter(branch -> branch.getTenant() != null && tenantId.equals(branch.getTenant().getId()))
                .toList();
        if (branches.size() != cleanIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Una o mas sedes no pertenecen al negocio");
        }
        RoleType role = RoleType.valueOf(normalizeInternalRole(user.getRol()));
        List<UserTenantRole> previous = userTenantRoleRepository.findByUserIdAndTenantIdAndRoleWithBranch(userId, tenantId, role);
        userTenantRoleRepository.deleteAllInBatch(previous);
        userTenantRoleRepository.flush();
        for (Branch branch : branches) {
            userTenantRoleRepository.save(UserTenantRole.builder().user(user).tenant(new Tenant(tenantId)).branch(branch).role(role).build());
        }
        user.setBranch(branches.get(0));
        user.setFechaActualizacion(LocalDateTime.now());
        userRepository.save(user);
        return responseWithBranches(user, tenantId);
    }

    public AppUserResponse responseWithBranches(AppUser user, Long tenantId) {
        AppUserResponse response = AppUserResponse.from(user);
        RoleType role;
        try {
            role = RoleType.valueOf(user.getRol() == null ? "" : user.getRol().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return response;
        }
        List<UserTenantRole> roles = userTenantRoleRepository.findByUserIdAndTenantIdAndRoleWithBranch(user.getId(), tenantId, role);
        response.setBranchIds(roles.stream().map(UserTenantRole::getBranch).filter(Objects::nonNull).map(Branch::getId).distinct().toList());
        response.setBranchNames(roles.stream().map(UserTenantRole::getBranch).filter(Objects::nonNull).map(Branch::getNombre).filter(Objects::nonNull).distinct().toList());
        response.setProfessionalProfileEnabled(userTenantRoleRepository.existsByUser_IdAndTenant_IdAndRole(user.getId(), tenantId, RoleType.BARBER));
        return response;
    }

    private void ensureProfessionalProfileRoles(AppUser user, Long tenantId, List<Long> requestedBranchIds, Branch fallbackBranch) {
        List<Long> cleanIds = requestedBranchIds == null ? List.of() : requestedBranchIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        List<Branch> branches = new ArrayList<>();
        if (!cleanIds.isEmpty()) {
            branches = branchRepository.findAllById(cleanIds).stream()
                    .filter(branch -> branch.getTenant() != null && tenantId.equals(branch.getTenant().getId()))
                    .toList();
            if (branches.size() != cleanIds.size()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Una o mas sedes profesionales no pertenecen al negocio");
            }
        } else if (fallbackBranch != null) {
            branches = List.of(fallbackBranch);
        } else if (user.getBranch() != null) {
            branches = List.of(user.getBranch());
        }

        if (branches.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona al menos una sede profesional");
        }

        for (Branch branch : branches) {
            ensureUserRoleForBranch(user, tenantId, RoleType.BARBER, branch);
        }
    }

    private void removeProfessionalProfileRoles(Long userId, Long tenantId) {
        List<UserTenantRole> previous = userTenantRoleRepository.findByUserIdAndTenantIdAndRoleWithBranch(userId, tenantId, RoleType.BARBER);
        userTenantRoleRepository.deleteAllInBatch(previous);
        userTenantRoleRepository.flush();
    }
    private void ensureUserRoleForBranch(AppUser user, Long tenantId, RoleType role, Branch branch) {
        if (branch == null || branch.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La sede es obligatoria para asignar el rol");
        }
        boolean exists = userTenantRoleRepository.existsByUser_IdAndTenant_IdAndBranch_IdAndRole(
                user.getId(), tenantId, branch.getId(), role
        );
        if (!exists) {
            userTenantRoleRepository.save(UserTenantRole.builder()
                    .user(user)
                    .tenant(new Tenant(tenantId))
                    .branch(branch)
                    .role(role)
                    .build());
        }
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
            if ("ADMIN".equals(role) || "CASHIER".equals(role) || "OWNER".equals(role)) {
                subscriptionService.validateAdminLimit(tenantId);
            }
        }

        String email = required(req.getEmail(), "El email es obligatorio").toLowerCase(Locale.ROOT);
        String password = required(req.getPassword(), "La contraseña es obligatoria");
        if (password.length() < 6) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña debe tener al menos 6 caracteres");
        if (userRepository.existsByEmailAndTenantId(email, tenantId)) {
            throw new RuntimeException("Email ya existe en esta barbería");
        }

        boolean professionalProfileRequested = Boolean.TRUE.equals(req.getPreserveProfessionalProfile())
                || Boolean.TRUE.equals(req.getProfessionalProfileEnabled());

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

        if (professionalProfileRequested || req.getCanSell() != null) {
            user.setCanSell(req.getCanSell() == null ? true : req.getCanSell());
        }

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

        if (professionalProfileRequested && ("ADMIN".equals(role) || "CASHIER".equals(role))) {
            ensureProfessionalProfileRoles(user, tenantId, req.getProfessionalBranchIds(), user.getBranch());
        }

        if ("ADMIN".equals(role) || "CASHIER".equals(role)) {
            adminPermissionService.createDefaultPermissionsForNewAdmin(tenantId, user.getId());
        }

        return responseWithBranches(user, tenantId);
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
        if (!List.of("OWNER", "ADMIN", "CASHIER", "BARBER").contains(role)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rol no permitido. Usa OWNER, ADMIN, CASHIER o BARBER");
        }
        return role;
    }

    private String required(String value, String message) {
        String v = value == null ? "" : value.trim();
        if (v.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        return v;
    }

    @Transactional
    public AppUserResponse cambiarRolUsuario(Long userId, String targetRole, Long branchId) {
        validarOwner();

        Long tenantId = TenantContext.getTenantId();
        String newRole = normalizeInternalRole(targetRole);

        if (!List.of("ADMIN", "CASHIER", "BARBER").contains(newRole)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Solo puedes cambiar entre ADMIN, CASHIER y BARBER"
            );
        }

        AppUser user = userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        boolean userHasOwnerRole = userTenantRoleRepository.existsByUser_IdAndTenant_IdAndRole(
                userId, tenantId, RoleType.OWNER);
        if (userHasOwnerRole) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "No puedes cambiar el rol del owner. Conserva OWNER y agrega BARBER como rol adicional."
            );
        }

        String currentRole = user.getRol() == null
                ? ""
                : user.getRol().trim().toUpperCase(Locale.ROOT);

        if ("OWNER".equals(currentRole)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "No puedes cambiar el rol del dueño principal desde esta pantalla"
            );
        }

        Branch branch = null;
        if (branchId != null) {
            branch = branchRepository.findByIdAndTenant_Id(branchId, tenantId)
                    .orElseThrow(() -> new RuntimeException("La sede no pertenece al tenant"));
        } else if (user.getBranch() != null) {
            branch = user.getBranch();
        } else {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Debes enviar una sede para este usuario"
            );
        }

        RoleType targetRoleType = RoleType.valueOf(newRole);
        List<UserTenantRole> tenantRoles = userTenantRoleRepository
                .findByUser_IdAndTenant_Id(userId, tenantId);
        boolean alreadyHasTargetRole = tenantRoles.stream()
                .anyMatch(role -> role.getRole() == targetRoleType);

        if ("BARBER".equals(newRole) && !alreadyHasTargetRole) {
            subscriptionService.validateBarberLimit(tenantId);
        }

        boolean currentRoleIsAdministrative = List.of("ADMIN", "CASHIER").contains(currentRole);
        if (("ADMIN".equals(newRole) || "CASHIER".equals(newRole))
                && !alreadyHasTargetRole
                && !currentRoleIsAdministrative) {
            subscriptionService.validateAdminLimit(tenantId);
        }

        user.setRol(newRole);
        user.setActivo(true);
        user.setBranch(branch);
        user.setFechaActualizacion(LocalDateTime.now());

        AppUser saved = userRepository.save(user);

        final Branch assignedBranch = branch;
        if (targetRoleType == RoleType.BARBER) {
            // Un administrador puede tener un perfil BARBER adicional. Al degradarlo,
            // conservamos esas filas profesionales y eliminamos solo los accesos
            // administrativos; convertir todas las filas provocaba una clave duplicada.
            List<UserTenantRole> obsoleteAdministrativeRoles = tenantRoles.stream()
                    .filter(role -> role.getRole() != RoleType.BARBER)
                    .toList();

            if (!obsoleteAdministrativeRoles.isEmpty()) {
                userTenantRoleRepository.deleteAllInBatch(obsoleteAdministrativeRoles);
                userTenantRoleRepository.flush();
            }

            ensureUserRoleForBranch(saved, tenantId, RoleType.BARBER, assignedBranch);
            adminPermissionService.revokeAllPermissionsForRoleChange(tenantId, userId);
        } else {
            // La accion de cambio de rol deja un unico rol administrativo. Borramos
            // y vaciamos primero para que un BARBER/ADMIN ya existente no choque con
            // la restriccion unica al insertar el nuevo rol.
            if (!tenantRoles.isEmpty()) {
                userTenantRoleRepository.deleteAllInBatch(tenantRoles);
                userTenantRoleRepository.flush();
            }

            userTenantRoleRepository.save(UserTenantRole.builder()
                    .user(saved)
                    .tenant(new Tenant(tenantId))
                    .branch(assignedBranch)
                    .role(targetRoleType)
                    .build());
            adminPermissionService.createDefaultPermissionsForNewAdmin(tenantId, userId);
        }

        return responseWithBranches(saved, tenantId);
    }
}

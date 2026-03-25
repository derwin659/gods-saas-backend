package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.AppUserResponse;
import com.gods.saas.domain.dto.CrearUsuarioRequest;
import com.gods.saas.domain.dto.request.BootstrapOwnerRequest;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.AppUserRepository;
import com.gods.saas.domain.repository.BranchRepository;
import com.gods.saas.domain.repository.TenantRepository;
import com.gods.saas.domain.repository.UserTenantRoleRepository;
import com.gods.saas.security.TenantContext;
import com.gods.saas.service.impl.impl.SubscriptionService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

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
        if (auth.getAuthorities().stream()
                .noneMatch(a -> a.getAuthority().equals("ROLE_OWNER"))) {
            throw new AccessDeniedException("Solo OWNER puede realizar esta acción");
        }
    }

    public String hashPassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    public AppUser crearUsuarioInterno(String nombre,
                                       String apellido,
                                       String email,
                                       String phone,
                                       String rol,
                                       Long branchId,
                                       String passwordHash) {

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
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    public List<AppUserResponse> getUsers() {
        Long tenantId = TenantContext.getTenantId();

        return userRepository.findByTenantId(tenantId)
                .stream()
                .map(AppUserResponse::from)
                .toList();
    }

    public AppUserResponse getUser(Long id) {
        Long tenantId = TenantContext.getTenantId();

        AppUser user = userRepository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        return AppUserResponse.from(user);
    }

    public AppUser actualizarUsuario(Long userId,
                                     String nombre,
                                     String apellido,
                                     String phone,
                                     Long branchId,
                                     String rol) {

        AppUser user = getById(userId);

        if (nombre != null) user.setNombre(nombre);
        if (apellido != null) user.setApellido(apellido);
        if (phone != null) user.setPhone(phone);
        if (branchId != null) user.setBranch(new Branch(branchId));
        if (rol != null) user.setRol(rol);

        user.setFechaActualizacion(LocalDateTime.now());

        return userRepository.save(user);
    }

    public void cambiarPassword(Long userId, String newPasswordHash) {
        AppUser user = getById(userId);
        user.setPasswordHash(newPasswordHash);
        user.setFechaActualizacion(LocalDateTime.now());
        userRepository.save(user);
    }

    public void cambiarEstado(Long userId, boolean activo) {
        AppUser user = getById(userId);
        user.setActivo(activo);
        userRepository.save(user);
    }


    public AppUserResponse crearUsuario(CrearUsuarioRequest req) {
        Long tenantId = TenantContext.getTenantId();
        String role = req.getRol() == null ? "" : req.getRol().trim().toUpperCase(Locale.ROOT);

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

        if (userRepository.existsByEmailAndTenantId(req.getEmail(), tenantId)) {
            throw new RuntimeException("Email ya existe en esta barbería");
        }

        AppUser user = AppUser.builder()
                .nombre(req.getNombre())
                .apellido(req.getApellido())
                .email(req.getEmail())
                .phone(req.getPhone())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
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

        Tenant tenantRef = new Tenant(tenantId);

        UserTenantRole userTenantRole = UserTenantRole.builder()
                .user(user)
                .tenant(tenantRef)
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
}
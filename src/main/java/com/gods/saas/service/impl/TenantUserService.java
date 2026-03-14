package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.*;
import com.gods.saas.domain.model.AppUser;
import com.gods.saas.domain.model.RoleType;
import com.gods.saas.domain.model.Tenant;
import com.gods.saas.domain.model.UserTenantRole;
import com.gods.saas.domain.repository.AppUserRepository;
import com.gods.saas.domain.repository.TenantRepository;
import com.gods.saas.domain.repository.UserTenantRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
public class TenantUserService {

    private final TenantRepository tenantRepo;
    private final AppUserRepository userRepo;
    private final UserTenantRoleRepository utrRepo;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Transactional
    public TenantUserDto createUser(Long tenantId, CreateTenantUserRequest req) {
        Tenant tenant = tenantRepo.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Tenant no encontrado"));

        if (req.getEmail() == null || req.getEmail().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Email es obligatorio");
        }
        if (req.getPassword() == null || req.getPassword().length() < 6) {
            throw new ResponseStatusException(BAD_REQUEST, "Password mínimo 6 caracteres");
        }

        RoleType role = parseRole(req.getRole());

        // si ya existe usuario por email, reusamos (multi-tenant real)
        AppUser user = userRepo.findByEmail(req.getEmail())
                .orElseGet(() -> {
                    AppUser u = new AppUser();
                    u.setNombre(req.getNombre());
                    u.setEmail(req.getEmail());
                    u.setPhone(req.getPhone());
                    u.setActivo(true);
                    u.setPasswordHash(passwordEncoder.encode(req.getPassword()));
                    return userRepo.save(u);
                });

        // validar que no esté ya asignado a este tenant
        utrRepo.findByUserIdAndTenantId(user.getId(), tenantId)
                .ifPresent(x -> { throw new ResponseStatusException(CONFLICT, "El usuario ya tiene acceso a este tenant"); });

        UserTenantRole utr = new UserTenantRole();
        utr.setUser(user);
        utr.setTenant(tenant);
        utr.setRole(role);
        utrRepo.save(utr);

        return TenantUserDto.builder()
                .id(user.getId())
                .name(user.getNombre())
                .email(user.getEmail())
                .active(user.getActivo())
                .role(role.name())
                .build();
    }



    @Transactional
    public TenantUserDto updateUser(Long tenantId, Long userId, UpdateTenantUserRequest req) {
        Tenant tenant = tenantRepo.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Tenant no encontrado"));

        UserTenantRole utr = utrRepo.findByUserIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "El usuario no pertenece a este tenant"));

        AppUser user = utr.getUser();

        if (req.getNombre() != null) user.setNombre(req.getNombre());
        if (req.getPhone() != null) user.setPhone(req.getPhone());
        if (req.getActive() != null) user.setActivo(req.getActive());

        if (req.getRole() != null && !req.getRole().isBlank()) {
            utr.setRole(parseRole(req.getRole()));
        }

        userRepo.save(user);
        utrRepo.save(utr);

        RoleType role = parseRole(req.getRole());

        return TenantUserDto.builder()
                .id(user.getId())
                .name(user.getNombre())
                .email(user.getEmail())
                .active(user.getActivo())
                .role(role.name())
                .build();
    }

    @Transactional
    public void removeUserFromTenant(Long tenantId, Long userId) {
        UserTenantRole utr = utrRepo.findByUserIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "El usuario no pertenece a este tenant"));
        utrRepo.delete(utr); // elimina relación (no borra AppUser)
    }

    private RoleType parseRole(String role) {
        try {
            return RoleType.valueOf(role);
        } catch (Exception e) {
            throw new ResponseStatusException(BAD_REQUEST, "Rol inválido: " + role);
        }
    }



        public List<TenantUserResponse> listUsers(Long tenantId) {
            return utrRepo.findByTenantId(tenantId).stream()
                    .map(utr -> TenantUserResponse.builder()
                            .id(utr.getUser().getId())
                            .nombre(utr.getUser().getNombre())
                            .email(utr.getUser().getEmail())
                            .role(utr.getRole().name())
                            .active(utr.getUser().getActivo())
                            .build()
                    ).toList();
        }

        public void createUser(Long tenantId, CreateUserRequest req, String photoUrl) {
            Tenant tenant = tenantRepo.findById(tenantId)
                    .orElseThrow(() -> new RuntimeException("Tenant no encontrado"));

            String rawPassword = Optional.ofNullable(req.getPassword())
                    .orElse(UUID.randomUUID().toString().substring(0, 8));


            AppUser user = AppUser.builder()
                    .tenant(tenant)
                    .nombre(req.getNombre())
                    .email(req.getEmail())
                    .passwordHash(passwordEncoder.encode(rawPassword))
                    .activo(true)
                    .photoUrl(photoUrl)
                    .build();

            userRepo.save(user);

            UserTenantRole utr = UserTenantRole.builder()
                    .tenant(tenant)
                    .user(user)
                    .role(RoleType.valueOf(req.getRole()))
                    .build();

            utrRepo.save(utr);
        }

        public void updateUser(Long tenantId, Long userId, UpdateUserRequest req,  String photoUrl) {
            UserTenantRole utr = utrRepo.findByUserIdAndTenantId(userId, tenantId)
                    .orElseThrow(() -> new RuntimeException("Usuario no pertenece al tenant"));

            AppUser user = utr.getUser();

            user.setNombre(req.getNombre());
            user.setPhotoUrl(photoUrl);

            userRepo.save(user);

            utr.setRole(RoleType.valueOf(req.getRole()));
            utrRepo.save(utr);
        }

        public void toggleUserStatus(Long tenantId, Long userId) {
            UserTenantRole utr = utrRepo.findByUserIdAndTenantId(userId, tenantId)
                    .orElseThrow(() -> new RuntimeException("Usuario no pertenece al tenant"));

            AppUser user = utr.getUser();
            user.setActivo(!user.getActivo());
            userRepo.save(user);
        }

        public void deleteUser(Long tenantId, Long userId) {
            UserTenantRole utr = utrRepo.findByUserIdAndTenantId(userId, tenantId)
                    .orElseThrow(() -> new RuntimeException("Usuario no pertenece al tenant"));

            utrRepo.delete(utr);
            userRepo.delete(utr.getUser());
        }

    public TenantUserDto getById(Long userId) {

        List<UserTenantRole> byId = utrRepo.findByUserId(userId);

        if(1 == byId.size()){

            return TenantUserDto.builder()
                    .id(byId.get(0).getId())
                    .name(byId.get(0).getUser().getNombre())
                    .email(byId.get(0).getUser().getEmail())
                    .active(byId.get(0).getUser().getActivo())
                    .role(byId.get(0).getRole().toString())
                    .build();
        }

       throw new RuntimeException("se encuentra mas de una vez el correo y debe ser unico");

    }
    }


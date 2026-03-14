package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.BarberCreateRequest;
import com.gods.saas.domain.dto.request.BarberStatusRequest;
import com.gods.saas.domain.dto.request.BarberUpdateRequest;
import com.gods.saas.domain.dto.response.BarberResponse;
import com.gods.saas.domain.model.AppUser;
import com.gods.saas.domain.model.Branch;
import com.gods.saas.domain.model.RoleType;
import com.gods.saas.domain.model.Tenant;
import com.gods.saas.domain.model.UserTenantRole;
import com.gods.saas.domain.repository.AppUserRepository;
import com.gods.saas.domain.repository.BranchRepository;
import com.gods.saas.domain.repository.UserTenantRoleRepository;
import com.gods.saas.service.impl.impl.OwnerBarberService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OwnerBarberServiceImpl implements OwnerBarberService {

    private final AppUserRepository appUserRepository;
    private final BranchRepository branchRepository;
    private final UserTenantRoleRepository userTenantRoleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public List<BarberResponse> listBarbers(Long tenantId, Long branchId) {
        List<AppUser> users = (branchId != null)
                ? appUserRepository.findByTenant_IdAndBranch_IdAndRol(tenantId, branchId, "BARBER")
                : appUserRepository.findByTenant_IdAndRol(tenantId, "BARBER");

        return users.stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public BarberResponse createBarber(Long tenantId, BarberCreateRequest request) {
        if (appUserRepository.existsByEmailAndTenant_Id(request.getEmail().trim(), tenantId)) {
            throw new IllegalArgumentException("Ya existe un usuario con ese email en este tenant.");
        }

        Branch branch = branchRepository.findByIdAndTenant_Id(request.getBranchId(), tenantId)
                .orElseThrow(() -> new EntityNotFoundException("La sede no existe o no pertenece al tenant."));

        Tenant tenantRef = new Tenant();
        tenantRef.setId(tenantId);

        AppUser user = AppUser.builder()
                .tenant(tenantRef)
                .branch(branch)
                .nombre(request.getNombre().trim())
                .apellido(request.getApellido().trim())
                .email(request.getEmail().trim().toLowerCase())
                .phone(request.getPhone() == null ? null : request.getPhone().trim())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .rol("BARBER")
                .activo(request.getActivo() != null ? request.getActivo() : true)
                .fechaCreacion(LocalDateTime.now())
                .salaryMode(false)
                .commissionScheme(null)
                .commissionPercentage(null)
                .build();

        AppUser saved = appUserRepository.save(user);

        UserTenantRole role = UserTenantRole.builder()
                .user(saved)
                .tenant(tenantRef)
                .branch(branch)
                .role(RoleType.BARBER)
                .build();

        userTenantRoleRepository.save(role);

        return toResponse(saved);
    }

    @Override
    @Transactional
    public BarberResponse updateBarber(Long tenantId, Long barberId, BarberUpdateRequest request) {
        AppUser barber = appUserRepository.findByIdAndTenant_Id(barberId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Barbero no encontrado."));

        if (!"BARBER".equalsIgnoreCase(barber.getRol())) {
            throw new IllegalArgumentException("El usuario indicado no es un barbero.");
        }

        if (appUserRepository.existsByEmailAndTenant_IdAndIdNot(
                request.getEmail().trim(),
                tenantId,
                barberId
        )) {
            throw new IllegalArgumentException("Ya existe otro usuario con ese email en este tenant.");
        }

        Branch branch = branchRepository.findByIdAndTenant_Id(request.getBranchId(), tenantId)
                .orElseThrow(() -> new EntityNotFoundException("La sede no existe o no pertenece al tenant."));

        barber.setNombre(request.getNombre().trim());
        barber.setApellido(request.getApellido().trim());
        barber.setEmail(request.getEmail().trim().toLowerCase());
        barber.setPhone(request.getPhone() == null ? null : request.getPhone().trim());
        barber.setBranch(branch);
        barber.setActivo(request.getActivo());
        barber.setFechaActualizacion(LocalDateTime.now());

        AppUser saved = appUserRepository.save(barber);

        UserTenantRole role = userTenantRoleRepository
                .findByUser_IdAndTenant_Id(barberId, tenantId)
                .orElse(null);

        if (role != null) {
            role.setBranch(branch);
            role.setRole(RoleType.BARBER);
            userTenantRoleRepository.save(role);
        }

        return toResponse(saved);
    }

    @Override
    @Transactional
    public BarberResponse updateStatus(Long tenantId, Long barberId, BarberStatusRequest request) {
        AppUser barber = appUserRepository.findByIdAndTenant_Id(barberId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Barbero no encontrado."));

        if (!"BARBER".equalsIgnoreCase(barber.getRol())) {
            throw new IllegalArgumentException("El usuario indicado no es un barbero.");
        }

        barber.setActivo(request.getActivo());
        barber.setFechaActualizacion(LocalDateTime.now());

        return toResponse(appUserRepository.save(barber));
    }

    private BarberResponse toResponse(AppUser user) {
        return BarberResponse.builder()
                .userId(user.getId())
                .nombre(user.getNombre())
                .apellido(user.getApellido())
                .email(user.getEmail())
                .phone(user.getPhone())
                .rol(user.getRol())
                .activo(user.getActivo())
                .branchId(user.getBranch() != null ? user.getBranch().getId() : null)
                .branchNombre(user.getBranch() != null ? user.getBranch().getNombre() : null)
                .build();
    }
}

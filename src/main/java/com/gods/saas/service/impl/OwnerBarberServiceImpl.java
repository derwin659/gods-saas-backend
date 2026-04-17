package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.BarberCreateRequest;
import com.gods.saas.domain.dto.request.BarberStatusRequest;
import com.gods.saas.domain.dto.request.BarberUpdateRequest;
import com.gods.saas.domain.dto.response.BarberResponse;
import com.gods.saas.domain.enums.SalaryFrequency;
import com.gods.saas.domain.model.AppUser;
import com.gods.saas.domain.model.Branch;
import com.gods.saas.domain.model.RoleType;
import com.gods.saas.domain.model.Tenant;
import com.gods.saas.domain.model.UserTenantRole;
import com.gods.saas.domain.repository.AppUserRepository;
import com.gods.saas.domain.repository.BranchRepository;
import com.gods.saas.domain.repository.UserTenantRoleRepository;
import com.gods.saas.exception.BusinessException;
import com.gods.saas.service.impl.impl.OwnerBarberService;
import com.gods.saas.service.impl.impl.SubscriptionService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OwnerBarberServiceImpl implements OwnerBarberService {

    private final AppUserRepository appUserRepository;
    private final BranchRepository branchRepository;
    private final UserTenantRoleRepository userTenantRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final SubscriptionService subscriptionService;

    @Override
    public List<BarberResponse> listBarbers(Long tenantId, Long branchId) {
        List<AppUser> users = (branchId != null)
                ? appUserRepository.findByTenantIdAndBranchIdAndRolWithBranch(tenantId, branchId, "BARBER")
                : appUserRepository.findByTenantIdAndRolWithBranch(tenantId, "BARBER");

        return users.stream()
                .map(this::toResponse)
                .toList();
    }

    private void applyCompensationModel(
            AppUser user,
            Boolean salaryMode,
            BigDecimal commissionPercentage,
            SalaryFrequency salaryFrequency,
            BigDecimal fixedSalaryAmount,
            LocalDate salaryStartDate
    ) {
        boolean isSalary = Boolean.TRUE.equals(salaryMode);
        user.setSalaryMode(isSalary);

        if (isSalary) {
            if (fixedSalaryAmount == null || fixedSalaryAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("Debes ingresar un sueldo fijo válido.");
            }
            if (salaryFrequency == null) {
                throw new BusinessException("Debes seleccionar la periodicidad del sueldo.");
            }

            user.setFixedSalaryAmount(fixedSalaryAmount);
            user.setSalaryFrequency(salaryFrequency);
            user.setSalaryStartDate(salaryStartDate);

            user.setCommissionScheme("SALARY");
            user.setCommissionPercentage(null);
        } else {
            if (commissionPercentage == null || commissionPercentage.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("Debes ingresar un porcentaje de comisión válido.");
            }

            user.setCommissionPercentage(commissionPercentage);
            user.setCommissionScheme("PERCENTAGE");

            user.setFixedSalaryAmount(null);
            user.setSalaryFrequency(null);
            user.setSalaryStartDate(null);
        }
    }

    @Override
    @Transactional
    public BarberResponse createBarber(Long tenantId, BarberCreateRequest request) {
        subscriptionService.validateSubscriptionActive(tenantId);
        subscriptionService.validateBarberLimit(tenantId);


        String email = normalizeRequired(request.getEmail(), "El email es obligatorio").toLowerCase();
        String nombre = normalizeRequired(request.getNombre(), "El nombre es obligatorio");
        String apellido = normalizeRequired(request.getApellido(), "El apellido es obligatorio");
        String password = normalizeRequired(request.getPassword(), "La contraseña es obligatoria");

        if (request.getBranchId() == null) {
            throw new BusinessException("La sede es obligatoria");
        }

        if (appUserRepository.existsByEmailAndTenant_Id(email, tenantId)) {
            throw new BusinessException("Ya existe un usuario con ese email en este tenant");
        }

        Branch branch = branchRepository.findByIdAndTenant_Id(request.getBranchId(), tenantId)
                .orElseThrow(() -> new EntityNotFoundException("La sede no existe o no pertenece al tenant"));

        Tenant tenantRef = new Tenant();
        tenantRef.setId(tenantId);

        AppUser user = AppUser.builder()
                .tenant(tenantRef)
                .branch(branch)
                .nombre(nombre)
                .apellido(apellido)
                .email(email)
                .phone(normalizeNullable(request.getPhone()))
                .passwordHash(passwordEncoder.encode(password))
                .rol("BARBER")
                .activo(request.getActivo() != null ? request.getActivo() : true)
                .fechaCreacion(LocalDateTime.now())
                .build();

        applyCompensationModel(
                user,
                request.getSalaryMode(),
                request.getCommissionPercentage(),
                request.getSalaryFrequency(),
                request.getFixedSalaryAmount(),
                request.getSalaryStartDate()
        );

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
            throw new BusinessException("El usuario indicado no es un barbero");
        }

        String email = normalizeRequired(request.getEmail(), "El email es obligatorio").toLowerCase();

        if (request.getBranchId() == null) {
            throw new BusinessException("La sede es obligatoria");
        }

        if (appUserRepository.existsByEmailAndTenant_IdAndIdNot(email, tenantId, barberId)) {
            throw new BusinessException("Ya existe otro usuario con ese email en este tenant");
        }
        System.out.printf("antes {}", request.getBranchId());
        Branch branch = branchRepository.findByIdAndTenant_Id(request.getBranchId(), tenantId)
                .orElseThrow(() -> new EntityNotFoundException("La sede no existe o no pertenece al tenant"));

        barber.setNombre(normalizeRequired(request.getNombre(), "El nombre es obligatorio"));
        barber.setApellido(normalizeRequired(request.getApellido(), "El apellido es obligatorio"));
        barber.setEmail(email);
        barber.setPhone(normalizeNullable(request.getPhone()));
        barber.setBranch(branch);
        System.out.printf("branch {}", branch);
        if (request.getActivo() != null) {
            barber.setActivo(request.getActivo());
        }

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

        applyCompensationModel(
                barber,
                request.getSalaryMode(),
                request.getCommissionPercentage(),
                request.getSalaryFrequency(),
                request.getFixedSalaryAmount(),
                request.getSalaryStartDate()
        );

        return toResponse(saved);
    }

    @Override
    @Transactional
    public BarberResponse updateStatus(Long tenantId, Long barberId, BarberStatusRequest request) {
        AppUser barber = appUserRepository.findByIdAndTenant_Id(barberId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Barbero no encontrado."));

        if (!"BARBER".equalsIgnoreCase(barber.getRol())) {
            throw new BusinessException("El usuario indicado no es un barbero");
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
                .salaryMode(user.getSalaryMode())
                .commissionPercentage(user.getCommissionPercentage())
                .salaryFrequency(user.getSalaryFrequency() != null ? user.getSalaryFrequency().name() : null)
                .fixedSalaryAmount(user.getFixedSalaryAmount())
                .salaryStartDate(user.getSalaryStartDate())
                .build();
    }

    private String normalizeRequired(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new BusinessException(message);
        }
        return value.trim();
    }

    private String normalizeNullable(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
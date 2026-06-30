package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.BarberBranchCompensationDto;
import com.gods.saas.domain.dto.request.SaveBarberBranchCompensationRequest;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import com.gods.saas.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class BarberBranchCompensationService {
    private final BarberBranchCompensationRepository compensationRepository;
    private final AppUserRepository appUserRepository;
    private final BranchRepository branchRepository;
    private final UserTenantRoleRepository userTenantRoleRepository;

    @Transactional(readOnly = true)
    public BarberBranchCompensationDto get(Long tenantId, Long branchId, Long barberUserId) {
        AppUser barber = resolveBarber(tenantId, branchId, barberUserId);
        Branch branch = branchRepository.findByIdAndTenant_Id(branchId, tenantId)
                .orElseThrow(() -> new BusinessException("La sede no pertenece al negocio"));

        return compensationRepository.findByTenant_IdAndBranch_IdAndBarber_Id(tenantId, branchId, barberUserId)
                .map(this::toDto)
                .orElseGet(() -> fallback(barber, branch));
    }

    @Transactional
    public BarberBranchCompensationDto save(
            Long tenantId,
            Long branchId,
            Long barberUserId,
            SaveBarberBranchCompensationRequest request
    ) {
        AppUser barber = resolveBarber(tenantId, branchId, barberUserId);
        Branch branch = branchRepository.findByIdAndTenant_Id(branchId, tenantId)
                .orElseThrow(() -> new BusinessException("La sede no pertenece al negocio"));
        validate(request);

        BarberBranchCompensation entity = compensationRepository
                .findByTenant_IdAndBranch_IdAndBarber_Id(tenantId, branchId, barberUserId)
                .orElseGet(() -> BarberBranchCompensation.builder()
                        .tenant(barber.getTenant())
                        .branch(branch)
                        .barber(barber)
                        .build());

        boolean salaryMode = Boolean.TRUE.equals(request.getSalaryMode());
        entity.setSalaryMode(salaryMode);
        entity.setCommissionPercentage(salaryMode ? null : request.getCommissionPercentage());
        entity.setSalaryFrequency(salaryMode ? request.getSalaryFrequency() : null);
        entity.setFixedSalaryAmount(salaryMode ? request.getFixedSalaryAmount() : null);
        entity.setSalaryStartDate(salaryMode ? request.getSalaryStartDate() : null);
        return toDto(compensationRepository.save(entity));
    }

    private AppUser resolveBarber(Long tenantId, Long branchId, Long barberUserId) {
        AppUser barber = appUserRepository.findByIdAndTenant_Id(barberUserId, tenantId)
                .orElseThrow(() -> new BusinessException("Profesional no encontrado"));
        if (!"BARBER".equalsIgnoreCase(barber.getRol())
                || !userTenantRoleRepository.existsByUser_IdAndTenant_IdAndBranch_Id(barberUserId, tenantId, branchId)) {
            throw new BusinessException("El profesional no está asignado a esta sede");
        }
        return barber;
    }

    private void validate(SaveBarberBranchCompensationRequest request) {
        if (Boolean.TRUE.equals(request.getSalaryMode())) {
            if (request.getFixedSalaryAmount() == null || request.getFixedSalaryAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("Ingresa un sueldo fijo válido");
            }
            if (request.getSalaryFrequency() == null) {
                throw new BusinessException("Selecciona la periodicidad del sueldo");
            }
        } else if (request.getCommissionPercentage() == null
                || request.getCommissionPercentage().compareTo(BigDecimal.ZERO) <= 0
                || request.getCommissionPercentage().compareTo(new BigDecimal("100")) > 0) {
            throw new BusinessException("La comisión debe ser mayor a 0 y menor o igual a 100");
        }
    }

    private BarberBranchCompensationDto fallback(AppUser barber, Branch branch) {
        return BarberBranchCompensationDto.builder()
                .branchId(branch.getId())
                .branchName(branch.getNombre())
                .salaryMode(Boolean.TRUE.equals(barber.getSalaryMode()))
                .commissionPercentage(barber.getCommissionPercentage())
                .salaryFrequency(barber.getSalaryFrequency())
                .fixedSalaryAmount(barber.getFixedSalaryAmount())
                .salaryStartDate(barber.getSalaryStartDate())
                .build();
    }

    private BarberBranchCompensationDto toDto(BarberBranchCompensation entity) {
        return BarberBranchCompensationDto.builder()
                .branchId(entity.getBranch().getId())
                .branchName(entity.getBranch().getNombre())
                .salaryMode(Boolean.TRUE.equals(entity.getSalaryMode()))
                .commissionPercentage(entity.getCommissionPercentage())
                .salaryFrequency(entity.getSalaryFrequency())
                .fixedSalaryAmount(entity.getFixedSalaryAmount())
                .salaryStartDate(entity.getSalaryStartDate())
                .build();
    }
}

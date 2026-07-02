package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.UpdateBarberServiceCommissionsRequest;
import com.gods.saas.domain.dto.response.BarberServiceCommissionResponse;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BarberServiceCommissionService {
    private final BarberServiceCommissionRepository repository;
    private final BarberBranchCompensationRepository branchCompensationRepository;
    private final AppUserRepository appUserRepository;
    private final BranchRepository branchRepository;
    private final ServiceRepository serviceRepository;
    private final UserTenantRoleRepository userTenantRoleRepository;
    private final BarberServiceAssignmentService assignmentService;
    private final GeneralAuditService auditService;

    @Transactional(readOnly = true)
    public BarberServiceCommissionResponse get(Long tenantId, Long branchId, Long barberId) {
        AppUser barber = validateScope(tenantId, branchId, barberId);
        BigDecimal defaultPercentage = branchCompensationRepository
                .findByTenant_IdAndBranch_IdAndBarber_Id(tenantId, branchId, barberId)
                .map(BarberBranchCompensation::getCommissionPercentage)
                .orElse(barber.getCommissionPercentage());
        if (defaultPercentage == null) defaultPercentage = BigDecimal.ZERO;

        Map<Long, BigDecimal> overrides = repository
                .findByTenant_IdAndBranch_IdAndBarber_IdOrderByService_NombreAsc(tenantId, branchId, barberId)
                .stream().collect(Collectors.toMap(row -> row.getService().getId(), BarberServiceCommission::getCommissionPercentage));

        BigDecimal fallback = defaultPercentage;
        List<BarberServiceCommissionResponse.Item> items = serviceRepository
                .findByTenant_IdAndActivoTrueOrderByNombreAsc(tenantId).stream()
                .filter(service -> assignmentService.canPerform(tenantId, branchId, barberId, service.getId()))
                .map(service -> BarberServiceCommissionResponse.Item.builder()
                        .serviceId(service.getId())
                        .serviceName(service.getNombre())
                        .imageUrl(service.getImageUrl())
                        .percentage(overrides.getOrDefault(service.getId(), fallback))
                        .custom(overrides.containsKey(service.getId()))
                        .build())
                .toList();

        return BarberServiceCommissionResponse.builder()
                .barberId(barberId).branchId(branchId)
                .defaultPercentage(defaultPercentage).services(items).build();
    }

    @Transactional
    public BarberServiceCommissionResponse update(Long tenantId, Long branchId, Long barberId, Long actorUserId, String actorRole, UpdateBarberServiceCommissionsRequest request) {
        AppUser barber = validateScope(tenantId, branchId, barberId);
        Branch branch = branchRepository.findByIdAndTenant_Id(branchId, tenantId).orElseThrow();
        Map<Long, BigDecimal> requested = request == null || request.getServicePercentages() == null
                ? Map.of() : request.getServicePercentages();
        requested.forEach((serviceId, percentage) -> {
            validatePercentage(percentage);
            ServiceEntity service = serviceRepository.findByIdAndTenant_Id(serviceId, tenantId)
                    .filter(item -> Boolean.TRUE.equals(item.getActivo()))
                    .orElseThrow(() -> new IllegalArgumentException("Servicio no disponible: " + serviceId));
            if (!assignmentService.canPerform(tenantId, branchId, barberId, serviceId))
                throw new IllegalArgumentException("El profesional no realiza " + service.getNombre() + " en esta sede");
        });

        List<BarberServiceCommission> current = repository
                .findByTenant_IdAndBranch_IdAndBarber_IdOrderByService_NombreAsc(tenantId, branchId, barberId);
        Map<Long, BigDecimal> before = current.stream().collect(Collectors.toMap(row -> row.getService().getId(), BarberServiceCommission::getCommissionPercentage));
        repository.deleteAll(current.stream().filter(row -> !requested.containsKey(row.getService().getId())).toList());
        repository.flush();
        Map<Long, BarberServiceCommission> existing = current.stream()
                .filter(row -> requested.containsKey(row.getService().getId()))
                .collect(Collectors.toMap(row -> row.getService().getId(), row -> row));
        requested.forEach((serviceId, percentage) -> {
            BarberServiceCommission row = existing.get(serviceId);
            if (row == null) {
                ServiceEntity service = serviceRepository.findByIdAndTenant_Id(serviceId, tenantId).orElseThrow();
                row = BarberServiceCommission.builder().tenant(barber.getTenant()).branch(branch).barber(barber).service(service).build();
            }
            row.setCommissionPercentage(percentage);
            repository.save(row);
        });
        auditService.record(tenantId, branchId, actorUserId, actorRole, "BARBER_SERVICE_COMMISSION", barberId, "UPDATE",
                "Comisiones variables por servicio actualizadas", before, requested);
        return get(tenantId, branchId, barberId);
    }

    @Transactional(readOnly = true)
    public BigDecimal resolvePercentage(Long tenantId, Long branchId, Long barberId, Long serviceId) {
        return repository.findByTenant_IdAndBranch_IdAndBarber_IdAndService_Id(tenantId, branchId, barberId, serviceId)
                .map(BarberServiceCommission::getCommissionPercentage)
                .orElseGet(() -> {
                    AppUser barber = appUserRepository.findByIdAndTenant_Id(barberId, tenantId).orElseThrow();
                    BigDecimal value = branchCompensationRepository.findByTenant_IdAndBranch_IdAndBarber_Id(tenantId, branchId, barberId)
                            .map(BarberBranchCompensation::getCommissionPercentage).orElse(barber.getCommissionPercentage());
                    return value == null ? BigDecimal.ZERO : value;
                });
    }

    private AppUser validateScope(Long tenantId, Long branchId, Long barberId) {
        branchRepository.findByIdAndTenant_Id(branchId, tenantId).orElseThrow(() -> new IllegalArgumentException("Sede no encontrada"));
        AppUser barber = appUserRepository.findByIdAndTenant_Id(barberId, tenantId).orElseThrow(() -> new IllegalArgumentException("Profesional no encontrado"));
        if (!userTenantRoleRepository.existsByUser_IdAndTenant_IdAndBranch_Id(barberId, tenantId, branchId))
            throw new IllegalArgumentException("El profesional no pertenece a esta sede");
        return barber;
    }

    private void validatePercentage(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(new BigDecimal("100")) > 0)
            throw new IllegalArgumentException("La comisión debe estar entre 0 y 100");
    }
}

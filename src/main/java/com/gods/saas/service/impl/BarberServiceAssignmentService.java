package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.response.BarberServiceAssignmentResponse;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service @RequiredArgsConstructor
public class BarberServiceAssignmentService {
    private final BarberBranchServiceRepository repository;
    private final AppUserRepository appUserRepository;
    private final BranchRepository branchRepository;
    private final ServiceRepository serviceRepository;
    private final UserTenantRoleRepository userTenantRoleRepository;
    private final GeneralAuditService auditService;

    @Transactional(readOnly = true)
    public BarberServiceAssignmentResponse get(Long tenantId, Long branchId, Long barberId) {
        validateScope(tenantId, branchId, barberId);
        var rows = repository.findByTenant_IdAndBranch_IdAndBarber_IdOrderByService_NombreAsc(tenantId, branchId, barberId);
        return BarberServiceAssignmentResponse.builder().barberId(barberId).branchId(branchId)
                .configured(!rows.isEmpty()).serviceIds(rows.stream().map(row -> row.getService().getId()).toList()).build();
    }

    @Transactional
    public BarberServiceAssignmentResponse update(Long tenantId, Long branchId, Long barberId, Long actorUserId, String actorRole, List<Long> requestedIds) {
        AppUser barber = validateScope(tenantId, branchId, barberId);
        List<Long> before = repository.findByTenant_IdAndBranch_IdAndBarber_IdOrderByService_NombreAsc(tenantId, branchId, barberId)
                .stream().map(row -> row.getService().getId()).toList();
        LinkedHashSet<Long> ids = new LinkedHashSet<>(requestedIds == null ? List.of() : requestedIds);
        if (ids.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona al menos un servicio o usa Restablecer todos");
        List<ServiceEntity> services = ids.stream().map(id -> serviceRepository.findByIdAndTenant_Id(id, tenantId)
                .filter(service -> Boolean.TRUE.equals(service.getActivo()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Servicio no disponible: " + id))).toList();
        List<BarberBranchService> currentRows =
                repository.findByTenant_IdAndBranch_IdAndBarber_IdOrderByService_NombreAsc(tenantId, branchId, barberId);
        Set<Long> requested = new LinkedHashSet<>(ids);
        List<BarberBranchService> removed = currentRows.stream()
                .filter(row -> !requested.contains(row.getService().getId()))
                .toList();
        repository.deleteAll(removed);
        repository.flush();

        Set<Long> existingIds = currentRows.stream()
                .filter(row -> requested.contains(row.getService().getId()))
                .map(row -> row.getService().getId())
                .collect(java.util.stream.Collectors.toSet());
        Branch branch = branchRepository.findByIdAndTenant_Id(branchId, tenantId).orElseThrow();
        for (ServiceEntity service : services) {
            if (!existingIds.contains(service.getId())) {
                repository.save(BarberBranchService.builder()
                        .tenant(new Tenant(tenantId)).branch(branch).barber(barber).service(service).build());
            }
        }
        auditService.record(tenantId, branchId, actorUserId, actorRole, "BARBER_SERVICE", barberId, "UPDATE", "Servicios habilitados actualizados", before, ids);
        return get(tenantId, branchId, barberId);
    }

    @Transactional
    public BarberServiceAssignmentResponse reset(Long tenantId, Long branchId, Long barberId, Long actorUserId, String actorRole) {
        validateScope(tenantId, branchId, barberId);
        List<Long> before = repository.findByTenant_IdAndBranch_IdAndBarber_IdOrderByService_NombreAsc(tenantId, branchId, barberId).stream().map(row -> row.getService().getId()).toList();
        repository.deleteByTenant_IdAndBranch_IdAndBarber_Id(tenantId, branchId, barberId);
        repository.flush();
        auditService.record(tenantId, branchId, actorUserId, actorRole, "BARBER_SERVICE", barberId, "RESET", "Restablecido a todos los servicios", before, Map.of("allActiveServices", true));
        return get(tenantId, branchId, barberId);
    }

    public boolean canPerform(Long tenantId, Long branchId, Long barberId, Long serviceId) {
        var rows = repository.findByTenant_IdAndBranch_IdAndBarber_IdOrderByService_NombreAsc(tenantId, branchId, barberId);
        return rows.isEmpty() || rows.stream().anyMatch(row -> row.getService().getId().equals(serviceId));
    }

    private AppUser validateScope(Long tenantId, Long branchId, Long barberId) {
        branchRepository.findByIdAndTenant_Id(branchId, tenantId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sede no encontrada"));
        AppUser barber = appUserRepository.findByIdAndTenant_Id(barberId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profesional no encontrado"));
        if (!userTenantRoleRepository.existsByUser_IdAndTenant_IdAndBranch_Id(barberId, tenantId, branchId))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El profesional no pertenece a esta sede");
        return barber;
    }
}

package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.OwnerBranchUpsertRequest;
import com.gods.saas.domain.dto.response.OwnerBranchResponse;
import com.gods.saas.domain.model.Branch;
import com.gods.saas.domain.model.Tenant;
import com.gods.saas.domain.repository.BranchRepository;
import com.gods.saas.domain.repository.TenantRepository;
import com.gods.saas.exception.BusinessException;
import com.gods.saas.service.impl.impl.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OwnerBranchService {

    private final BranchRepository branchRepository;
    private final TenantRepository tenantRepository;
    private final SubscriptionService subscriptionService;

    @Transactional(readOnly = true)
    public List<OwnerBranchResponse> listBranches(Long tenantId) {
        return branchRepository.findByTenant_IdOrderByNombreAsc(tenantId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OwnerBranchResponse> listActiveBranches(Long tenantId) {
        return branchRepository.findByTenant_IdAndActivoTrueOrderByNombreAsc(tenantId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public OwnerBranchResponse createBranch(Long tenantId, OwnerBranchUpsertRequest request) {
        subscriptionService.validateBranchLimit(tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("No se encontró el tenant"));

        final String nombre = normalizeRequired(request.nombre());

        if (branchRepository.existsByTenant_IdAndNombreIgnoreCase(tenantId, nombre)) {
            throw new BusinessException("Ya existe una sede con ese nombre");
        }

        Branch branch = Branch.builder()
                .tenant(tenant)
                .nombre(nombre)
                .direccion(normalizeNullable(request.direccion()))
                .telefono(normalizeNullable(request.telefono()))
                .activo(request.activo() != null ? request.activo() : true)
                .fechaCreacion(LocalDateTime.now())
                .build();

        Branch saved = branchRepository.save(branch);
        return toResponse(saved);
    }

    @Transactional
    public OwnerBranchResponse updateBranch(Long tenantId, Long branchId, OwnerBranchUpsertRequest request) {
        Branch branch = branchRepository.findByIdAndTenant_Id(branchId, tenantId)
                .orElseThrow(() -> new BusinessException("No se encontró la sede"));

        final String nombre = normalizeRequired(request.nombre());

        if (branchRepository.existsByTenant_IdAndNombreIgnoreCaseAndIdNot(tenantId, nombre, branchId)) {
            throw new BusinessException("Ya existe otra sede con ese nombre");
        }

        branch.setNombre(nombre);
        branch.setDireccion(normalizeNullable(request.direccion()));
        branch.setTelefono(normalizeNullable(request.telefono()));

        if (request.activo() != null) {
            branch.setActivo(request.activo());
        }

        Branch saved = branchRepository.save(branch);
        return toResponse(saved);
    }

    @Transactional
    public void updateStatus(Long tenantId, Long branchId, Boolean activo) {
        Branch branch = branchRepository.findByIdAndTenant_Id(branchId, tenantId)
                .orElseThrow(() -> new BusinessException("No se encontró la sede"));

        branch.setActivo(Boolean.TRUE.equals(activo));
        branchRepository.save(branch);
    }

    private OwnerBranchResponse toResponse(Branch branch) {
        return new OwnerBranchResponse(
                branch.getId(),
                branch.getNombre(),
                branch.getDireccion(),
                branch.getTelefono(),
                branch.getActivo()
        );
    }

    private String normalizeRequired(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new BusinessException("El nombre de la sede es obligatorio");
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
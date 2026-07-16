package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.OwnerBranchUpsertRequest;
import com.gods.saas.domain.dto.response.OwnerBranchResponse;
import com.gods.saas.domain.model.Branch;
import com.gods.saas.domain.model.Tenant;
import com.gods.saas.domain.repository.BranchRepository;
import com.gods.saas.domain.repository.ClientBusinessFollowRepository;
import com.gods.saas.domain.repository.TenantRepository;
import com.gods.saas.exception.BusinessException;
import com.gods.saas.service.impl.impl.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OwnerBranchService {

    private final BranchRepository branchRepository;
    private final ClientBusinessFollowRepository followRepository;
    private final TenantRepository tenantRepository;
    private final SubscriptionService subscriptionService;
    private final CloudinaryStorageService cloudinaryStorageService;

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
                .orElseThrow(() -> new BusinessException("No se encontrÃƒÂ³ el tenant"));

        final String nombre = normalizeRequired(request.nombre());

        if (branchRepository.existsByTenant_IdAndNombreIgnoreCase(tenantId, nombre)) {
            throw new BusinessException("Ya existe una sede con ese nombre");
        }

        Branch branch = Branch.builder()
                .tenant(tenant)
                .nombre(nombre)
                .direccion(normalizeNullable(request.direccion()))
                .telefono(normalizeNullable(request.telefono()))
                .ciudad(normalizeNullable(request.ciudad()))
                .latitude(request.latitude())
                .longitude(request.longitude())
                .publicVisible(Boolean.TRUE.equals(request.publicVisible()))
                .directoryEnabled(Boolean.TRUE.equals(request.directoryEnabled()))
                .publicDescription(normalizeNullable(request.publicDescription()))
                .activo(request.activo() != null ? request.activo() : true)
                .fechaCreacion(LocalDateTime.now())
                .build();

        Branch saved = branchRepository.save(branch);
        return toResponse(saved);
    }

    @Transactional
    public OwnerBranchResponse updateBranch(Long tenantId, Long branchId, OwnerBranchUpsertRequest request) {
        Branch branch = branchRepository.findByIdAndTenant_Id(branchId, tenantId)
                .orElseThrow(() -> new BusinessException("No se encontrÃƒÂ³ la sede"));

        final String nombre = normalizeRequired(request.nombre());

        if (branchRepository.existsByTenant_IdAndNombreIgnoreCaseAndIdNot(tenantId, nombre, branchId)) {
            throw new BusinessException("Ya existe otra sede con ese nombre");
        }

        branch.setNombre(nombre);
        branch.setDireccion(normalizeNullable(request.direccion()));
        branch.setTelefono(normalizeNullable(request.telefono()));
        branch.setCiudad(normalizeNullable(request.ciudad()));
        branch.setLatitude(request.latitude());
        branch.setLongitude(request.longitude());
        branch.setPublicVisible(Boolean.TRUE.equals(request.publicVisible()));
        branch.setDirectoryEnabled(Boolean.TRUE.equals(request.directoryEnabled()));
        branch.setPublicDescription(normalizeNullable(request.publicDescription()));

        if (request.activo() != null) {
            branch.setActivo(request.activo());
        }

        Branch saved = branchRepository.save(branch);
        return toResponse(saved);
    }

    @Transactional
    public void updateStatus(Long tenantId, Long branchId, Boolean activo) {
        Branch branch = branchRepository.findByIdAndTenant_Id(branchId, tenantId)
                .orElseThrow(() -> new BusinessException("No se encontrÃƒÂ³ la sede"));

        branch.setActivo(Boolean.TRUE.equals(activo));
        branchRepository.save(branch);
    }

    @Transactional
    public OwnerBranchResponse uploadImage(Long tenantId, Long branchId, MultipartFile file) {
        Branch branch = branchRepository.findByIdAndTenant_Id(branchId, tenantId)
                .orElseThrow(() -> new BusinessException("No se encontrÃƒÂ³ la sede"));

        String oldPublicId = branch.getImagePublicId();

        CloudinaryStorageService.UploadResult result =
                cloudinaryStorageService.uploadBranchImage(tenantId, branchId, file);

        branch.setImageUrl(result.getSecureUrl());
        branch.setImagePublicId(result.getPublicId());

        Branch saved = branchRepository.save(branch);

        if (oldPublicId != null && !oldPublicId.isBlank()) {
            cloudinaryStorageService.deleteImage(oldPublicId);
        }

        return toResponse(saved);
    }

    @Transactional
    public OwnerBranchResponse deleteImage(Long tenantId, Long branchId) {
        Branch branch = branchRepository.findByIdAndTenant_Id(branchId, tenantId)
                .orElseThrow(() -> new BusinessException("No se encontrÃƒÂ³ la sede"));

        String oldPublicId = branch.getImagePublicId();

        branch.setImageUrl(null);
        branch.setImagePublicId(null);

        Branch saved = branchRepository.save(branch);

        if (oldPublicId != null && !oldPublicId.isBlank()) {
            cloudinaryStorageService.deleteImage(oldPublicId);
        }

        return toResponse(saved);
    }

    private OwnerBranchResponse toResponse(Branch branch) {
        return new OwnerBranchResponse(
                branch.getId(),
                branch.getNombre(),
                branch.getDireccion(),
                branch.getTelefono(),
                branch.getActivo(),
                branch.getImageUrl(),
                branch.getCiudad(),
                branch.getLatitude(),
                branch.getLongitude(),
                Boolean.TRUE.equals(branch.getPublicVisible()),
                Boolean.TRUE.equals(branch.getDirectoryEnabled()),
                branch.getPublicDescription(),
                followRepository.countByTenant_Id(branch.getTenant().getId())
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
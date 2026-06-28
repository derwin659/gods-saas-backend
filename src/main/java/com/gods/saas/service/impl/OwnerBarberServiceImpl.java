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
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OwnerBarberServiceImpl implements OwnerBarberService {

    private final AppUserRepository appUserRepository;
    private final BranchRepository branchRepository;
    private final UserTenantRoleRepository userTenantRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final SubscriptionService subscriptionService;
    private final CloudinaryStorageService cloudinaryStorageService;
    private final GeneralAuditService generalAuditService;

    @Override
    public List<BarberResponse> listBarbers(Long tenantId, Long branchId) {
        List<AppUser> users = (branchId != null)
                ? userTenantRoleRepository.findActiveUsersByTenantBranchAndRole(tenantId, branchId, RoleType.BARBER)
                : appUserRepository.findByTenantIdAndRolWithBranch(tenantId, "BARBER");

        return users.stream()
                .map(user -> toResponse(user, tenantId))
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

    private List<Branch> resolveAssignedBranches(Long tenantId, Long primaryBranchId, List<Long> branchIds, Boolean allBranches) {
        if (Boolean.TRUE.equals(allBranches)) {
            List<Branch> branches = branchRepository.findByTenant_IdAndActivoTrueOrderByNombreAsc(tenantId);
            if (branches.isEmpty()) {
                throw new BusinessException("No hay sedes activas para asignar.");
            }
            return branches;
        }

        Map<Long, Branch> selected = new LinkedHashMap<>();

        if (branchIds != null) {
            for (Long id : branchIds) {
                if (id == null || id <= 0) continue;
                Branch branch = branchRepository.findByIdAndTenant_Id(id, tenantId)
                        .orElseThrow(() -> new EntityNotFoundException("La sede no existe o no pertenece al tenant"));
                selected.put(branch.getId(), branch);
            }
        }

        if (selected.isEmpty() && primaryBranchId != null) {
            Branch branch = branchRepository.findByIdAndTenant_Id(primaryBranchId, tenantId)
                    .orElseThrow(() -> new EntityNotFoundException("La sede no existe o no pertenece al tenant"));
            selected.put(branch.getId(), branch);
        }

        if (selected.isEmpty()) {
            throw new BusinessException("Selecciona al menos una sede.");
        }

        return new ArrayList<>(selected.values());
    }

    private void replaceBarberBranchRoles(AppUser user, Tenant tenantRef, List<Branch> branches) {
        List<Long> selectedBranchIds = branches.stream()
                .map(Branch::getId)
                .filter(id -> id != null)
                .distinct()
                .toList();

        List<UserTenantRole> existingRoles = userTenantRoleRepository
                .findByUserIdAndTenantIdAndRoleWithBranch(user.getId(), tenantRef.getId(), RoleType.BARBER);

        for (UserTenantRole existingRole : existingRoles) {
            Long existingBranchId = existingRole.getBranch() != null ? existingRole.getBranch().getId() : null;
            if (existingBranchId == null || !selectedBranchIds.contains(existingBranchId)) {
                userTenantRoleRepository.delete(existingRole);
            }
        }

        List<Long> existingBranchIds = existingRoles.stream()
                .map(UserTenantRole::getBranch)
                .filter(branch -> branch != null && branch.getId() != null)
                .map(Branch::getId)
                .distinct()
                .toList();

        for (Branch branch : branches) {
            if (existingBranchIds.contains(branch.getId())) {
                continue;
            }

            UserTenantRole role = UserTenantRole.builder()
                    .user(user)
                    .tenant(tenantRef)
                    .branch(branch)
                    .role(RoleType.BARBER)
                    .build();
            userTenantRoleRepository.save(role);
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

        if (request.getBranchId() == null && (request.getBranchIds() == null || request.getBranchIds().isEmpty())) {
            throw new BusinessException("La sede es obligatoria");
        }

        if (appUserRepository.existsByEmailAndTenant_Id(email, tenantId)) {
            throw new BusinessException("Ya existe un usuario con ese email en este tenant");
        }

        List<Branch> assignedBranches = resolveAssignedBranches(
                tenantId,
                request.getBranchId(),
                request.getBranchIds(),
                request.getAllBranches()
        );
        Branch branch = assignedBranches.get(0);

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
                .canSell(request.getCanSell() == null ? true : request.getCanSell())
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
        replaceBarberBranchRoles(saved, tenantRef, assignedBranches);

        return toResponse(saved, tenantId);
    }

    @Override
    @Transactional
    public BarberResponse updateBarber(Long tenantId, Long actorUserId, String actorRole, Long barberId, BarberUpdateRequest request) {
        AppUser barber = appUserRepository.findByIdAndTenant_Id(barberId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Barbero no encontrado."));

        if (!"BARBER".equalsIgnoreCase(barber.getRol())) {
            throw new BusinessException("El usuario indicado no es un barbero");
        }

        List<Long> previousBranchIds = userTenantRoleRepository
                .findByUserIdAndTenantIdAndRoleWithBranch(barberId, tenantId, RoleType.BARBER)
                .stream().map(UserTenantRole::getBranch).filter(value -> value != null)
                .map(Branch::getId).filter(value -> value != null).distinct().sorted().toList();

        String email = normalizeRequired(request.getEmail(), "El email es obligatorio").toLowerCase();

        if (request.getBranchId() == null && (request.getBranchIds() == null || request.getBranchIds().isEmpty())) {
            throw new BusinessException("La sede es obligatoria");
        }

        if (appUserRepository.existsByEmailAndTenant_IdAndIdNot(email, tenantId, barberId)) {
            throw new BusinessException("Ya existe otro usuario con ese email en este tenant");
        }
        List<Branch> assignedBranches = resolveAssignedBranches(
                tenantId,
                request.getBranchId(),
                request.getBranchIds(),
                request.getAllBranches()
        );
        Branch branch = assignedBranches.get(0);

        barber.setNombre(normalizeRequired(request.getNombre(), "El nombre es obligatorio"));
        barber.setApellido(normalizeRequired(request.getApellido(), "El apellido es obligatorio"));
        barber.setEmail(email);
        barber.setPhone(normalizeNullable(request.getPhone()));
        barber.setBranch(branch);
        if (request.getActivo() != null) {
            barber.setActivo(request.getActivo());
        }
        barber.setCanSell(request.getCanSell() == null ? true : request.getCanSell());

        barber.setFechaActualizacion(LocalDateTime.now());

        applyCompensationModel(
                barber,
                request.getSalaryMode(),
                request.getCommissionPercentage(),
                request.getSalaryFrequency(),
                request.getFixedSalaryAmount(),
                request.getSalaryStartDate()
        );

        AppUser saved = appUserRepository.save(barber);
        Tenant tenantRef = new Tenant();
        tenantRef.setId(tenantId);
        replaceBarberBranchRoles(saved, tenantRef, assignedBranches);

        List<Long> newBranchIds = assignedBranches.stream().map(Branch::getId).distinct().sorted().toList();
        if (!previousBranchIds.equals(newBranchIds)) {
            generalAuditService.record(
                    tenantId, branch.getId(), actorUserId, actorRole,
                    "BARBER_BRANCH_ASSIGNMENT", barberId, "UPDATE",
                    "Sedes asignadas al profesional actualizadas", previousBranchIds, newBranchIds
            );
        }

        return toResponse(saved, tenantId);
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

        return toResponse(appUserRepository.save(barber), tenantId);
    }

    private BarberResponse toResponse(AppUser user, Long tenantId) {
        List<UserTenantRole> roles = userTenantRoleRepository
                .findByUserIdAndTenantIdAndRoleWithBranch(user.getId(), tenantId, RoleType.BARBER);
        List<Long> branchIds = roles.stream()
                .map(UserTenantRole::getBranch)
                .filter(branch -> branch != null && branch.getId() != null)
                .map(Branch::getId)
                .distinct()
                .toList();
        List<String> branchNames = roles.stream()
                .map(UserTenantRole::getBranch)
                .filter(branch -> branch != null && branch.getNombre() != null && !branch.getNombre().isBlank())
                .map(Branch::getNombre)
                .distinct()
                .toList();

        Long primaryBranchId = branchIds.isEmpty() ? null : branchIds.get(0);
        String primaryBranchName = branchNames.isEmpty() ? null : branchNames.get(0);

        return BarberResponse.builder()
                .userId(user.getId())
                .nombre(user.getNombre())
                .apellido(user.getApellido())
                .email(user.getEmail())
                .phone(user.getPhone())
                .rol(user.getRol())
                .activo(user.getActivo())
                .canSell(user.getCanSell() == null ? true : user.getCanSell())
                .branchId(primaryBranchId)
                .branchNombre(primaryBranchName)
                .branchIds(branchIds)
                .branchNombres(branchNames)
                .photoUrl(user.getPhotoUrl())
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

    @Override
    @Transactional
    public BarberResponse uploadPhoto(Long tenantId, Long barberId, MultipartFile file) {
        AppUser barber = appUserRepository.findByIdAndTenant_Id(barberId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Barbero no encontrado."));

        if (!userTenantRoleRepository.existsByUser_IdAndTenant_IdAndRole(barberId, tenantId, RoleType.BARBER)) {
            throw new BusinessException("El usuario indicado no es un barbero");
        }

        String oldPublicId = barber.getPhotoPublicId();

        CloudinaryStorageService.UploadResult result =
                cloudinaryStorageService.uploadBarberPhoto(tenantId, barberId, file);

        barber.setPhotoUrl(result.getSecureUrl());
        barber.setPhotoPublicId(result.getPublicId());
        barber.setFechaActualizacion(LocalDateTime.now());

        AppUser saved = appUserRepository.save(barber);

        if (oldPublicId != null && !oldPublicId.isBlank()) {
            cloudinaryStorageService.deleteImage(oldPublicId);
        }

        return toResponse(saved, tenantId);
    }

    @Override
    @Transactional
    public BarberResponse deletePhoto(Long tenantId, Long barberId) {
        AppUser barber = appUserRepository.findByIdAndTenant_Id(barberId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Barbero no encontrado."));

        if (!userTenantRoleRepository.existsByUser_IdAndTenant_IdAndRole(barberId, tenantId, RoleType.BARBER)) {
            throw new BusinessException("El usuario indicado no es un barbero");
        }

        String oldPublicId = barber.getPhotoPublicId();

        barber.setPhotoUrl(null);
        barber.setPhotoPublicId(null);
        barber.setFechaActualizacion(LocalDateTime.now());

        AppUser saved = appUserRepository.save(barber);

        if (oldPublicId != null && !oldPublicId.isBlank()) {
            cloudinaryStorageService.deleteImage(oldPublicId);
        }

        return toResponse(saved, tenantId);
    }

}

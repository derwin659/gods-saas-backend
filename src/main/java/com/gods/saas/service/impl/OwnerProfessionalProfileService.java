package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.OwnerProfessionalProfileRequest;
import com.gods.saas.domain.dto.response.BarberResponse;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import com.gods.saas.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class OwnerProfessionalProfileService {
    private final AppUserRepository appUserRepository;
    private final BranchRepository branchRepository;
    private final UserTenantRoleRepository roleRepository;
    private final GeneralAuditService auditService;

    @Transactional(readOnly = true)
    public Map<String,Object> status(Long tenantId, Long ownerId) {
        List<UserTenantRole> roles = roleRepository.findByUserIdAndTenantIdAndRoleWithBranch(ownerId, tenantId, RoleType.BARBER);
        return response(roles);
    }

    @Transactional
    public Map<String,Object> enable(Long tenantId, Long ownerId, OwnerProfessionalProfileRequest request) {
        AppUser owner = requireOwner(tenantId, ownerId);
        List<Branch> branches = Boolean.TRUE.equals(request.getAllBranches())
                ? branchRepository.findByTenant_IdAndActivoTrueOrderByNombreAsc(tenantId)
                : resolveBranches(tenantId, request.getBranchIds());
        if (branches.isEmpty()) throw new BusinessException("Selecciona al menos una sede");
        List<UserTenantRole> previous = roleRepository.findByUserIdAndTenantIdAndRoleWithBranch(ownerId, tenantId, RoleType.BARBER);
        roleRepository.deleteAll(previous);
        roleRepository.flush();
        Tenant tenant = new Tenant(tenantId);
        for (Branch branch : branches) roleRepository.save(UserTenantRole.builder().user(owner).tenant(tenant).branch(branch).role(RoleType.BARBER).build());
        if (owner.getBranch() == null) { owner.setBranch(branches.get(0)); appUserRepository.save(owner); }
        auditService.record(tenantId, branches.get(0).getId(), ownerId, "OWNER", "OWNER_PROFESSIONAL", ownerId, "ENABLE",
                "El dueño fue habilitado para atender clientes", previous.stream().map(r -> r.getBranch().getId()).toList(), branches.stream().map(Branch::getId).toList());
        return response(roleRepository.findByUserIdAndTenantIdAndRoleWithBranch(ownerId, tenantId, RoleType.BARBER));
    }

    @Transactional
    public Map<String,Object> disable(Long tenantId, Long ownerId) {
        requireOwner(tenantId, ownerId);
        List<UserTenantRole> previous = roleRepository.findByUserIdAndTenantIdAndRoleWithBranch(ownerId, tenantId, RoleType.BARBER);
        roleRepository.deleteAll(previous);
        roleRepository.flush();
        auditService.record(tenantId, null, ownerId, "OWNER", "OWNER_PROFESSIONAL", ownerId, "DISABLE",
                "El dueño dejó de atender clientes", previous.stream().map(r -> r.getBranch().getId()).toList(), Map.of("enabled", false));
        return response(List.of());
    }

    private AppUser requireOwner(Long tenantId, Long ownerId) {
        return appUserRepository.findByIdAndTenant_Id(ownerId, tenantId)
                .orElseThrow(() -> new BusinessException("No se encontró la cuenta del dueño"));
    }
    private List<Branch> resolveBranches(Long tenantId, List<Long> ids) {
        LinkedHashMap<Long,Branch> values = new LinkedHashMap<>();
        for (Long id : ids == null ? List.<Long>of() : ids) branchRepository.findByIdAndTenant_Id(id, tenantId).filter(b -> Boolean.TRUE.equals(b.getActivo())).ifPresent(b -> values.put(b.getId(), b));
        return new ArrayList<>(values.values());
    }
    private Map<String,Object> response(List<UserTenantRole> roles) {
        List<Map<String,Object>> branches = roles.stream().filter(r -> r.getBranch()!=null).map(r -> Map.<String,Object>of("id",r.getBranch().getId(),"name",r.getBranch().getNombre())).toList();
        return Map.of("enabled", !branches.isEmpty(), "branches", branches);
    }
}

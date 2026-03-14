package com.gods.saas.service.impl;

import com.gods.saas.domain.model.AppUser;
import com.gods.saas.domain.model.RoleType;
import com.gods.saas.domain.model.Tenant;
import com.gods.saas.domain.model.UserTenantRole;
import com.gods.saas.domain.repository.AppUserRepository;
import com.gods.saas.domain.repository.TenantRepository;
import com.gods.saas.domain.repository.UserTenantRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserTenantRoleService {

    private final UserTenantRoleRepository repo;
    private final AppUserRepository userRepo;
    private final TenantRepository tenantRepo;

    public UserTenantRole assignRole(Long userId, Long tenantId, RoleType role) {

        AppUser user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Tenant tenant = tenantRepo.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant no encontrado"));

        // Verificar si ya existe la relación
        repo.findByUserIdAndTenantId(userId, tenantId).ifPresent(r -> {
            throw new RuntimeException("El usuario ya tiene un rol en esta barbería");
        });

        UserTenantRole utr = new UserTenantRole();
        utr.setUser(user);
        utr.setTenant(tenant);
        utr.setRole(role);

        return repo.save(utr);
    }

    public List<UserTenantRole> getTenantsOfUser(Long userId) {
        return repo.findByUserId(userId);
    }
}


package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.AdminPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdminPermissionRepository extends JpaRepository<AdminPermission, Long> {

    List<AdminPermission> findByTenant_IdAndUser_IdOrderByPermissionKeyAsc(
            Long tenantId,
            Long userId
    );

    Optional<AdminPermission> findByTenant_IdAndUser_IdAndPermissionKey(
            Long tenantId,
            Long userId,
            String permissionKey
    );

    boolean existsByTenant_IdAndUser_IdAndPermissionKeyAndEnabledTrue(
            Long tenantId,
            Long userId,
            String permissionKey
    );

    void deleteByTenant_IdAndUser_Id(Long tenantId, Long userId);
}
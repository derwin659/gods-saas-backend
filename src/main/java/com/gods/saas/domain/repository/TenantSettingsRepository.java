package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.TenantSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenantSettingsRepository extends JpaRepository<TenantSettings, Long> {
    Optional<TenantSettings> findByTenantId(Long tenantId);

    Optional<TenantSettings> findByTenant_Id(Long tenantId);
}

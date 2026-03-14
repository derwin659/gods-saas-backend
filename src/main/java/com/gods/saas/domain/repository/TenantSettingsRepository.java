package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.TenantSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantSettingsRepository extends JpaRepository<TenantSettings, Long> {
    Optional<TenantSettings> findByTenantId(Long tenantId);
}

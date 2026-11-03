package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.TenantSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenantSettingsRepository extends JpaRepository<TenantSettings, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from TenantSettings s where s.tenant.id=:tenant")
    Optional<TenantSettings> findForWhatsappUpdate(@org.springframework.data.repository.query.Param("tenant") Long tenant);
    Optional<TenantSettings> findByTenantId(Long tenantId);

    Optional<TenantSettings> findByTenant_Id(Long tenantId);
}

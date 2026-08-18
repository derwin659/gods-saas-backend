package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.ElectronicInvoicingSettings;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface ElectronicInvoicingSettingsRepository extends JpaRepository<ElectronicInvoicingSettings, Long> {
    Optional<ElectronicInvoicingSettings> findByTenantId(Long tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ElectronicInvoicingSettings s where s.tenant.id = :tenantId")
    Optional<ElectronicInvoicingSettings> findLockedByTenantId(@Param("tenantId") Long tenantId);
}

package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.AffiliatedDiscoveryEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AffiliatedDiscoveryEventRepository extends JpaRepository<AffiliatedDiscoveryEvent, Long> {
    long countByTenant_IdAndBranch_IdAndEventType(Long tenantId, Long branchId, String eventType);
    long countByTenant_IdAndEventType(Long tenantId, String eventType);
}
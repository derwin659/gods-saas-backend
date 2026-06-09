package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.LocalConsumptionOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LocalConsumptionOrderRepository extends JpaRepository<LocalConsumptionOrder, Long> {
    List<LocalConsumptionOrder> findByTenant_IdAndBranch_IdOrderByCreatedAtDesc(Long tenantId, Long branchId);

    List<LocalConsumptionOrder> findByTenant_IdAndBranch_IdAndStatusOrderByCreatedAtDesc(
            Long tenantId,
            Long branchId,
            String status
    );

    Optional<LocalConsumptionOrder> findByIdAndTenant_Id(Long id, Long tenantId);
}

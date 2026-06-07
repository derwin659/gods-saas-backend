package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.ProductOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductOrderRepository extends JpaRepository<ProductOrder, Long> {
    List<ProductOrder> findByTenant_IdAndBranch_IdOrderByCreatedAtDesc(Long tenantId, Long branchId);

    List<ProductOrder> findByTenant_IdAndBranch_IdAndStatusOrderByCreatedAtDesc(
            Long tenantId,
            Long branchId,
            String status
    );

    Optional<ProductOrder> findByIdAndTenant_Id(Long id, Long tenantId);
}

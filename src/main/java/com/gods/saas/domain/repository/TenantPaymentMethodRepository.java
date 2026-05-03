package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.TenantPaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenantPaymentMethodRepository extends JpaRepository<TenantPaymentMethod, Long> {

    List<TenantPaymentMethod> findByTenant_IdAndActiveTrueOrderBySortOrderAscDisplayNameAsc(Long tenantId);

    List<TenantPaymentMethod> findByTenant_IdAndBranch_IdAndActiveTrueOrderBySortOrderAscDisplayNameAsc(
            Long tenantId,
            Long branchId
    );

    Optional<TenantPaymentMethod> findByIdAndTenant_IdAndActiveTrue(Long id, Long tenantId);
}
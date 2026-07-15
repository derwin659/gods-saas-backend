package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.CashFundMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CashFundMovementRepository extends JpaRepository<CashFundMovement, Long> {

    List<CashFundMovement> findByTenant_IdAndBranch_IdOrderByMovementDateDesc(Long tenantId, Long branchId);
}
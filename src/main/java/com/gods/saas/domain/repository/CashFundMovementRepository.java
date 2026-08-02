package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.CashFundMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface CashFundMovementRepository extends JpaRepository<CashFundMovement, Long> {

    List<CashFundMovement> findByTenant_IdAndBranch_IdOrderByMovementDateDesc(Long tenantId, Long branchId);

    @Query("""
        select movement from CashFundMovement movement
        join fetch movement.branch branch
        left join fetch movement.actorUser actor
        left join fetch movement.cashRegister cashRegister
        where movement.tenant.id = :tenantId
          and (:branchId is null or branch.id = :branchId)
          and movement.movementDate >= :start
          and movement.movementDate < :end
        order by movement.movementDate desc
        """)
    List<CashFundMovement> findReportMovements(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}
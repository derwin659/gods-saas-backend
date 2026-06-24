package com.gods.saas.domain.repository;

import com.gods.saas.domain.enums.CashRegisterStatus;
import com.gods.saas.domain.model.CashRegister;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CashRegisterRepository extends JpaRepository<CashRegister, Long> {

    Optional<CashRegister> findByTenant_IdAndBranch_IdAndStatus(Long tenantId, Long branchId, CashRegisterStatus status);

    boolean existsByTenant_IdAndBranch_IdAndStatus(Long tenantId, Long branchId, CashRegisterStatus status);

    Optional<CashRegister> findByIdAndTenant_Id(Long id, Long tenantId);

    List<CashRegister> findByTenant_IdAndBranch_IdAndOpenedAtBetweenOrderByOpenedAtDesc(
            Long tenantId,
            Long branchId,
            LocalDateTime from,
            LocalDateTime to
    );

    Optional<CashRegister> findFirstByTenant_IdAndBranch_IdAndOpenedAtGreaterThanEqualAndOpenedAtLessThanOrderByOpenedAtDesc(
            Long tenantId,
            Long branchId,
            LocalDateTime from,
            LocalDateTime to
    );
    @Query("""
        select distinct cr
        from CashRegister cr
        where cr.tenant.id = :tenantId
          and cr.branch.id = :branchId
          and (
            (cr.openedAt >= :from and cr.openedAt < :to)
            or exists (
                select 1 from Sale s
                where s.cashRegister = cr
                  and (s.paymentValidationStatus is null or s.paymentValidationStatus = 'APPROVED')
                  and coalesce(s.saleDate, s.fechaCreacion) >= :from
                  and coalesce(s.saleDate, s.fechaCreacion) < :to
            )
            or exists (
                select 1 from CashMovement cm
                where cm.cashRegister = cr
                  and cm.movementDate >= :from
                  and cm.movementDate < :to
            )
          )
        order by cr.openedAt desc
        """)
    List<CashRegister> findHistoryByOpenedAtOrActivityBetween(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );










}
package com.gods.saas.domain.repository;

import com.gods.saas.domain.enums.BarberPaymentStatus;
import com.gods.saas.domain.model.BarberPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BarberPaymentRepository extends JpaRepository<BarberPayment, Long> {

    List<BarberPayment> findByTenant_IdAndBranch_IdAndBarberUser_IdOrderByCreatedAtDesc(
            Long tenantId,
            Long branchId,
            Long barberUserId
    );

    @Query("""
        select coalesce(sum(bp.amountPaid), 0)
        from BarberPayment bp
        where bp.tenant.id = :tenantId
          and (:branchId is null or bp.branch.id = :branchId)
          and bp.barberUser.id = :barberUserId
          and bp.status <> com.gods.saas.domain.enums.BarberPaymentStatus.CANCELLED
          and bp.periodFrom <= :periodTo
          and bp.periodTo >= :periodFrom
        """)
    BigDecimal sumPaidInPeriod(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId,
            @Param("barberUserId") Long barberUserId,
            @Param("periodFrom") LocalDate periodFrom,
            @Param("periodTo") LocalDate periodTo
    );



    @Query("""
        select max(bp.periodTo)
        from BarberPayment bp
        where bp.tenant.id = :tenantId
          and (:branchId is null or bp.branch.id = :branchId)
          and bp.barberUser.id = :barberUserId
          and bp.status = com.gods.saas.domain.enums.BarberPaymentStatus.PAID
          and bp.periodFrom <= :periodTo
          and bp.periodTo >= :periodFrom
        """)
    Optional<LocalDate> findLatestPaidPeriodToOverlapping(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId,
            @Param("barberUserId") Long barberUserId,
            @Param("periodFrom") LocalDate periodFrom,
            @Param("periodTo") LocalDate periodTo
    );


    @Query("""
        select bp
        from BarberPayment bp
        where bp.tenant.id = :tenantId
          and (:branchId is null or bp.branch.id = :branchId)
          and bp.status in :statuses
          and bp.periodFrom <= :periodTo
          and bp.periodTo >= :periodFrom
        order by bp.createdAt desc
        """)
    List<BarberPayment> findByTenantAndBranchAndStatusesAndOverlap(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId,
            @Param("statuses") List<BarberPaymentStatus> statuses,
            @Param("periodFrom") LocalDate periodFrom,
            @Param("periodTo") LocalDate periodTo
    );

    @Query("""
        select coalesce(sum(bp.amountPaid), 0)
        from BarberPayment bp
        where bp.tenant.id = :tenantId
          and (:branchId is null or bp.branch.id = :branchId)
          and bp.status <> com.gods.saas.domain.enums.BarberPaymentStatus.CANCELLED
          and bp.createdAt >= :fromDateTime
          and bp.createdAt < :toDateTime
        """)
    BigDecimal sumTotalPaidByRange(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId,
            @Param("fromDateTime") java.time.LocalDateTime fromDateTime,
            @Param("toDateTime") java.time.LocalDateTime toDateTime
    );

    boolean existsByCashMovement_Id(Long cashMovementId);

    Optional<BarberPayment> findByCashMovement_Id(Long cashMovementId);
}
package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.CashMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface CashMovementRepository extends JpaRepository<CashMovement, Long> {

    List<CashMovement> findByCashRegister_IdOrderByMovementDateDesc(Long cashRegisterId);


    Optional<CashMovement> findByIdAndTenant_Id(Long movementId, Long tenantId);

    @Query("""
    select coalesce(sum(cm.amount), 0)
    from CashMovement cm
    where cm.tenant.id = :tenantId
      and (:branchId is null or cm.branch.id = :branchId)
      and cm.barberUser.id = :barberUserId
      and cm.type = com.gods.saas.domain.enums.CashMovementType.ADVANCE_BARBER
      and cm.movementDate >= :start
      and cm.movementDate < :end
    """)
    BigDecimal sumAdvancesByBarberAndRange(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId,
            @Param("barberUserId") Long barberUserId,
            @Param("start") java.time.LocalDateTime start,
            @Param("end") java.time.LocalDateTime end
    );

    @Query("""
    select coalesce(sum(cm.amount), 0)
    from CashMovement cm
    where cm.tenant.id = :tenantId
      and (:branchId is null or cm.branch.id = :branchId)
      and cm.type = com.gods.saas.domain.enums.CashMovementType.EXPENSE
      and cm.movementDate >= :start
      and cm.movementDate < :end
    """)
    BigDecimal sumGeneralExpensesByRange(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId,
            @Param("start") java.time.LocalDateTime start,
            @Param("end") java.time.LocalDateTime end
    );



    @Query("""
    select coalesce(sum(cm.amount), 0)
    from CashMovement cm
    where cm.tenant.id = :tenantId
      and (:branchId is null or cm.branch.id = :branchId)
      and cm.type = com.gods.saas.domain.enums.CashMovementType.ADVANCE_BARBER
      and cm.movementDate >= :start
      and cm.movementDate < :end
    """)
    BigDecimal sumBarberAdvancesByRange(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId,
            @Param("start") java.time.LocalDateTime start,
            @Param("end") java.time.LocalDateTime end
    );

    @Query("""
    select coalesce(sum(cm.amount), 0)
    from CashMovement cm
    where cm.tenant.id = :tenantId
      and (:branchId is null or cm.branch.id = :branchId)
      and cm.type = com.gods.saas.domain.enums.CashMovementType.PAYMENT_BARBER
      and cm.movementDate >= :start
      and cm.movementDate < :end
    """)
    BigDecimal sumBarberPaymentsByRange(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId,
            @Param("start") java.time.LocalDateTime start,
            @Param("end") java.time.LocalDateTime end
    );


    @Query(value = """
    with days as (
        select generate_series(cast(:start as date), cast(:endDate as date), interval '1 day')::date as report_date
    ),
    sales as (
        select
            cast(s.fecha_creacion as date) as report_date,
            coalesce(sum(s.total), 0) as total_sales
        from sale s
        where s.tenant_id = :tenantId
          and (:branchId is null or s.branch_id = :branchId)
          and s.fecha_creacion >= :start
          and s.fecha_creacion < :end
        group by cast(s.fecha_creacion as date)
    ),
    expenses as (
        select
            cast(cm.movement_date as date) as report_date,
            coalesce(sum(case when cm.type = 'EXPENSE' then cm.amount else 0 end), 0) as operational_expenses,
            coalesce(sum(case when cm.type = 'ADVANCE_BARBER' then cm.amount else 0 end), 0) as barber_advances,
            coalesce(sum(case when cm.type = 'PAYMENT_BARBER' then cm.amount else 0 end), 0) as barber_payments
        from cash_movement cm
        where cm.tenant_id = :tenantId
          and (:branchId is null or cm.branch_id = :branchId)
          and cm.movement_date >= :start
          and cm.movement_date < :end
        group by cast(cm.movement_date as date)
    )
    select
        d.report_date as reportDate,
        coalesce(s.total_sales, 0) as totalSales,
        coalesce(e.operational_expenses, 0) as operationalExpenses,
        coalesce(e.barber_advances, 0) as barberAdvances,
        coalesce(e.barber_payments, 0) as barberPayments
    from days d
    left join sales s on s.report_date = d.report_date
    left join expenses e on e.report_date = d.report_date
    order by d.report_date asc
    """, nativeQuery = true)
    List<com.gods.saas.domain.repository.projection.DailyProfitabilityProjection> getDailyProfitability(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId,
            @Param("start") java.time.LocalDateTime start,
            @Param("end") java.time.LocalDateTime end,
            @Param("endDate") java.time.LocalDate endDate
    );
}

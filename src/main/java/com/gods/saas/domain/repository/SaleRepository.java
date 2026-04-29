package com.gods.saas.domain.repository;

import com.gods.saas.domain.dto.response.BarberReportSummaryResponse;
import com.gods.saas.domain.model.Sale;
import com.gods.saas.domain.repository.projection.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SaleRepository extends JpaRepository<Sale, Long> {

    @Query(value = """
    SELECT
        c.customer_id AS customerId,
        COALESCE(
            NULLIF(TRIM(CONCAT(COALESCE(c.nombres, ''), ' ', COALESCE(c.apellidos, ''))), ''),
            'Cliente'
        ) AS nombre,
        c.telefono AS telefono,
        MAX(COALESCE(s.sale_date, s.fecha_creacion)) AS ultimaVisita
    FROM customer c
    JOIN sale s
      ON s.customer_id = c.customer_id
    WHERE c.tenant_id = :tenantId
      AND s.tenant_id = :tenantId
      AND c.customer_id IS NOT NULL
    GROUP BY c.customer_id, c.nombres, c.apellidos, c.telefono
    HAVING MAX(COALESCE(s.sale_date, s.fecha_creacion)) <= NOW() - CAST((:daysInactive || ' days') AS interval)
    ORDER BY MAX(COALESCE(s.sale_date, s.fecha_creacion)) ASC
    """, nativeQuery = true)
    List<CustomerCampaignAudienceProjection> findInactiveCustomers(Long tenantId, Integer daysInactive);

    @Query(value = """
    select
        cast(COALESCE(s.sale_date, s.fecha_creacion) as date) as saleDate,
        coalesce(sum(s.total), 0) as totalSales,
        count(s.sale_id) as salesCount
    from sale s
    where s.tenant_id = :tenantId
      and (:branchId is null or s.branch_id = :branchId)
      and COALESCE(s.sale_date, s.fecha_creacion) >= :start
      and COALESCE(s.sale_date, s.fecha_creacion) < :end
    group by cast(COALESCE(s.sale_date, s.fecha_creacion) as date)
    order by cast(COALESCE(s.sale_date, s.fecha_creacion) as date) asc
    """, nativeQuery = true)
    List<DailySalesReportProjection> getDailySalesReport(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query(value = """
        select
            coalesce(se.nombre, p.nombre, 'Item') as serviceName,
            count(si.sale_item_id) as timesSold,
            coalesce(sum(si.subtotal), 0) as totalAmount
        from sale s
        join sale_item si on si.sale_id = s.sale_id
        left join service se on se.service_id = si.service_id
        left join product p on p.product_id = si.product_id
        where s.tenant_id = :tenantId
          and (:branchId is null or s.branch_id = :branchId)
          and COALESCE(s.sale_date, s.fecha_creacion) >= :start
          and COALESCE(s.sale_date, s.fecha_creacion) < :end
        group by coalesce(se.nombre, p.nombre, 'Item')
        order by count(si.sale_item_id) desc, coalesce(sum(si.subtotal), 0) desc
        """, nativeQuery = true)
    List<TopServiceReportProjection> getTopServicesReport(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query(value = """
        select coalesce(sum(
            case
                when exists (select 1 from sale_payment spx where spx.sale_id = s.sale_id)
                    then coalesce(sp.amount, 0)
                when (
                    (upper(:paymentMethod) in ('EFECTIVO','CASH') and upper(trim(coalesce(s.metodo_pago, ''))) in ('EFECTIVO','CASH')) or
                    (upper(:paymentMethod) in ('TARJETA','CARD') and upper(trim(coalesce(s.metodo_pago, ''))) in ('TARJETA','CARD')) or
                    (upper(:paymentMethod) in ('GRATIS','FREE') and upper(trim(coalesce(s.metodo_pago, ''))) in ('GRATIS','FREE')) or
                    upper(trim(coalesce(s.metodo_pago, ''))) = upper(:paymentMethod)
                ) then s.total
                else 0
            end
        ), 0)
        from sale s
        left join sale_payment sp
          on sp.sale_id = s.sale_id
         and (
              (upper(:paymentMethod) in ('EFECTIVO','CASH') and upper(trim(sp.method)) in ('EFECTIVO','CASH')) or
              (upper(:paymentMethod) in ('TARJETA','CARD') and upper(trim(sp.method)) in ('TARJETA','CARD')) or
              (upper(:paymentMethod) in ('GRATIS','FREE') and upper(trim(sp.method)) in ('GRATIS','FREE')) or
              upper(trim(sp.method)) = upper(:paymentMethod)
         )
        where s.tenant_id = :tenantId
          and (:branchId is null or s.branch_id = :branchId)
          and COALESCE(s.sale_date, s.fecha_creacion) >= :start
          and COALESCE(s.sale_date, s.fecha_creacion) < :end
        """, nativeQuery = true)
    BigDecimal getTotalByPaymentMethod(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId,
            @Param("paymentMethod") String paymentMethod,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
        select coalesce(sum(s.total), 0)
        from Sale s
        where s.tenant.id = :tenantId
          and s.branch.id = :branchId
          and s.user.id = :barberId
          and coalesce(s.saleDate, s.fechaCreacion) >= :start
          and coalesce(s.saleDate, s.fechaCreacion) < :end
        """)
    BigDecimal sumTodaySales(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId,
            @Param("barberId") Long barberId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
        select count(s)
        from Sale s
        where s.tenant.id = :tenantId
          and s.branch.id = :branchId
          and s.user.id = :barberId
          and coalesce(s.saleDate, s.fechaCreacion) >= :start
          and coalesce(s.saleDate, s.fechaCreacion) < :end
        """)
    long countTodayServices(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId,
            @Param("barberId") Long barberId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
        select s
        from Sale s
        where s.tenant.id = :tenantId
          and s.branch.id = :branchId
          and s.user.id = :userId
          and coalesce(s.saleDate, s.fechaCreacion) >= :start
          and coalesce(s.saleDate, s.fechaCreacion) < :end
        """)
    List<Sale> findByTenant_IdAndBranch_IdAndUser_IdAndFechaCreacionBetween(
            Long tenantId,
            Long branchId,
            Long userId,
            LocalDateTime start,
            LocalDateTime end
    );

    @Query("""
        select
            cast(coalesce(s.saleDate, s.fechaCreacion) as date) as fecha,
            coalesce(sum(s.total), 0) as ventas
        from Sale s
        where s.tenant.id = :tenantId
          and s.branch.id = :branchId
          and s.user.id = :barberId
          and coalesce(s.saleDate, s.fechaCreacion) >= :start
          and coalesce(s.saleDate, s.fechaCreacion) < :end
        group by cast(coalesce(s.saleDate, s.fechaCreacion) as date)
        order by cast(coalesce(s.saleDate, s.fechaCreacion) as date) asc
        """)
    List<BarberCommissionDailyProjection> findDailySalesByBarber(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId,
            @Param("barberId") Long barberId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
        select coalesce(sum(s.total), 0)
        from Sale s
        where s.cashRegister.id = :cashRegisterId
        """)
    BigDecimal sumTotalByCashRegisterId(@Param("cashRegisterId") Long cashRegisterId);

    @Query(value = """
        select coalesce(sum(
            case
                when exists (select 1 from sale_payment spx where spx.sale_id = s.sale_id)
                    then coalesce(sp.amount, 0)
                when upper(trim(coalesce(s.metodo_pago, ''))) in ('EFECTIVO', 'CASH')
                    then s.total
                else 0
            end
        ), 0)
        from sale s
        left join sale_payment sp
          on sp.sale_id = s.sale_id
         and upper(trim(sp.method)) in ('EFECTIVO', 'CASH')
        where s.cash_register_id = :cashRegisterId
        """, nativeQuery = true)
    BigDecimal sumCashTotalByCashRegisterId(@Param("cashRegisterId") Long cashRegisterId);

    Optional<Sale> findByIdAndTenant_Id(Long saleId, Long tenantId);

    @Query("""
        select s
        from Sale s
        where s.tenant.id = :tenantId
          and s.branch.id = :branchId
          and coalesce(s.saleDate, s.fechaCreacion) >= :from
          and coalesce(s.saleDate, s.fechaCreacion) < :to
        order by coalesce(s.saleDate, s.fechaCreacion) desc
        """)
    List<Sale> findByTenant_IdAndBranch_IdAndFechaCreacionBetweenOrderByFechaCreacionDesc(
            Long tenantId,
            Long branchId,
            LocalDateTime from,
            LocalDateTime to
    );

    @Query(value = """
        select
            u.user_id as barberId,
            u.nombre as barberName,
            coalesce(sum(si.subtotal), 0) as totalSales,
            count(distinct s.sale_id) as salesCount
        from sale s
        join sale_item si on si.sale_id = s.sale_id
        join app_user u on u.user_id = si.barber_user_id
        where s.tenant_id = :tenantId
          and (:branchId is null or s.branch_id = :branchId)
          and COALESCE(s.sale_date, s.fecha_creacion) >= :start
          and COALESCE(s.sale_date, s.fecha_creacion) < :end
        group by u.user_id, u.nombre
        order by coalesce(sum(si.subtotal), 0) desc, u.nombre asc
        """, nativeQuery = true)
    List<BarberSalesSummaryProjection> getBarberSalesSummary(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
        select coalesce(sum(s.total), 0)
        from Sale s
        where s.tenant.id = :tenantId
          and (:branchId is null or s.branch.id = :branchId)
          and coalesce(s.saleDate, s.fechaCreacion) >= :start
          and coalesce(s.saleDate, s.fechaCreacion) < :end
        """)
    BigDecimal getTotalSalesByRange(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
        select count(s)
        from Sale s
        where s.tenant.id = :tenantId
          and (:branchId is null or s.branch.id = :branchId)
          and coalesce(s.saleDate, s.fechaCreacion) >= :start
          and coalesce(s.saleDate, s.fechaCreacion) < :end
        """)
    Long countSalesByRange(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query(value = """
        select count(distinct si.barber_user_id)
        from sale s
        join sale_item si on si.sale_id = s.sale_id
        where s.tenant_id = :tenantId
          and (:branchId is null or s.branch_id = :branchId)
          and si.barber_user_id is not null
          and COALESCE(s.sale_date, s.fecha_creacion) >= :start
          and COALESCE(s.sale_date, s.fecha_creacion) < :end
        """, nativeQuery = true)
    Long countActiveBarbersByRange(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query(value = """
        select
            s.sale_id as saleId,
            coalesce(
                nullif(trim(concat(coalesce(c.nombres, ''), ' ', coalesce(c.apellidos, ''))), ''),
                'Cliente'
            ) as customerName,
            coalesce(
                string_agg(
                    distinct coalesce(se.nombre, p.nombre, 'Item'),
                    ', '
                ),
                ''
            ) as serviceNames,
            s.total as total,
            coalesce(s.metodo_pago, '') as paymentMethod,
            COALESCE(s.sale_date, s.fecha_creacion) as createdAt
        from sale s
        left join customer c on c.customer_id = s.customer_id
        left join sale_item si on si.sale_id = s.sale_id
        left join service se on se.service_id = si.service_id
        left join product p on p.product_id = si.product_id
        where s.tenant_id = :tenantId
          and (:branchId is null or s.branch_id = :branchId)
          and si.barber_user_id = :barberId
          and COALESCE(s.sale_date, s.fecha_creacion) >= :start
          and COALESCE(s.sale_date, s.fecha_creacion) < :end
        group by s.sale_id, c.nombres, c.apellidos, s.total, s.metodo_pago, s.sale_date, s.fecha_creacion
        order by COALESCE(s.sale_date, s.fecha_creacion) desc
        """, nativeQuery = true)
    List<BarberSaleDetailProjection> getBarberSaleDetails(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId,
            @Param("barberId") Long barberId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
        select
            b.id as branchId,
            b.nombre as branchName,
            coalesce(sum(s.total), 0) as totalSales,
            count(distinct s.id) as totalServices,
            count(distinct s.customer.id) as totalClients,
            coalesce(avg(s.total), 0) as averageTicket
        from Sale s
        join s.branch b
        where s.tenant.id = :tenantId
          and (:fromDateTime is null or coalesce(s.saleDate, s.fechaCreacion) >= :fromDateTime)
          and (:toDateTime is null or coalesce(s.saleDate, s.fechaCreacion) < :toDateTime)
        group by b.id, b.nombre
        order by coalesce(sum(s.total), 0) desc
        """)
    List<BranchReportSummaryProjection> getBranchSummary(
            @Param("tenantId") Long tenantId,
            @Param("fromDateTime") LocalDateTime fromDateTime,
            @Param("toDateTime") LocalDateTime toDateTime
    );

    @Query("""
        select
            u.id as barberId,
            u.nombre as barberName,
            b.id as branchId,
            b.nombre as branchName,
            coalesce(sum(si.subtotal), 0) as totalSales,
            count(distinct s.id) as totalServices,
            count(distinct s.customer.id) as totalClients,
            coalesce(avg(s.total), 0) as averageTicket
        from Sale s
        join s.items si
        join si.barberUser u
        join s.branch b
        where s.tenant.id = :tenantId
          and (:branchId is null or b.id = :branchId)
          and (:fromDateTime is null or coalesce(s.saleDate, s.fechaCreacion) >= :fromDateTime)
          and (:toDateTime is null or coalesce(s.saleDate, s.fechaCreacion) < :toDateTime)
        group by u.id, u.nombre, b.id, b.nombre
        order by coalesce(sum(si.subtotal), 0) desc
        """)
    List<BarberReportSummaryResponse> getBarberSummary(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId,
            @Param("fromDateTime") LocalDateTime fromDateTime,
            @Param("toDateTime") LocalDateTime toDateTime
    );

    @Query("""
        select coalesce(sum(s.total), 0)
        from Sale s
        where s.tenant.id = :tenantId
          and (:branchId is null or s.branch.id = :branchId)
          and coalesce(s.saleDate, s.fechaCreacion) >= :start
          and coalesce(s.saleDate, s.fechaCreacion) < :end
        """)
    BigDecimal sumSalesByDay(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
        select coalesce(avg(s.total), 0)
        from Sale s
        where s.tenant.id = :tenantId
          and (:branchId is null or s.branch.id = :branchId)
          and coalesce(s.saleDate, s.fechaCreacion) >= :start
          and coalesce(s.saleDate, s.fechaCreacion) < :end
        """)
    BigDecimal averageTicketByDay(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    Optional<Sale> findByIdAndTenant_IdAndBranch_Id(Long saleId, Long tenantId, Long branchId);

    @Query("""
        select s
        from Sale s
        where s.tenant.id = :tenantId
          and s.branch.id = :branchId
          and coalesce(s.saleDate, s.fechaCreacion) >= :from
          and coalesce(s.saleDate, s.fechaCreacion) < :to
        order by coalesce(s.saleDate, s.fechaCreacion) desc
        """)
    List<Sale> findByTenant_IdAndBranch_IdAndFechaCreacionGreaterThanEqualAndFechaCreacionLessThanOrderByFechaCreacionDesc(
            Long tenantId,
            Long branchId,
            LocalDateTime from,
            LocalDateTime to
    );

    @Query("""
        select coalesce(sum(si.subtotal), 0)
        from SaleItem si
        join si.sale s
        where s.tenant.id = :tenantId
          and (:branchId is null or s.branch.id = :branchId)
          and si.barberUser.id = :barberUserId
          and coalesce(s.saleDate, s.fechaCreacion) >= :start
          and coalesce(s.saleDate, s.fechaCreacion) < :end
        """)
    BigDecimal sumBarberItemSalesByRange(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId,
            @Param("barberUserId") Long barberUserId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
        select coalesce(sum(s.tipAmount), 0)
        from Sale s
        where s.tenant.id = :tenantId
          and (:branchId is null or s.branch.id = :branchId)
          and s.tipBarberUser.id = :barberUserId
          and coalesce(s.saleDate, s.fechaCreacion) >= :start
          and coalesce(s.saleDate, s.fechaCreacion) < :end
        """)
    BigDecimal sumBarberTipsByRange(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId,
            @Param("barberUserId") Long barberUserId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query(value = """
        select coalesce(sum(
            case
                when exists (select 1 from sale_payment spx where spx.sale_id = s.sale_id)
                    then coalesce(sp.amount, 0)
                when upper(trim(coalesce(s.metodo_pago, ''))) in ('EFECTIVO', 'CASH')
                    then s.total
                else 0
            end
        ), 0)
        from sale s
        left join sale_payment sp
          on sp.sale_id = s.sale_id
         and upper(trim(sp.method)) in ('EFECTIVO', 'CASH')
        where s.tenant_id = :tenantId
          and (:branchId is null or s.branch_id = :branchId)
          and COALESCE(s.sale_date, s.fecha_creacion) >= :start
          and COALESCE(s.sale_date, s.fecha_creacion) < :end
        """, nativeQuery = true)
    BigDecimal getCashSalesByRange(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
        select s
        from Sale s
        where s.tenant.id = :tenantId
          and s.branch.id = :branchId
          and coalesce(s.saleDate, s.fechaCreacion) >= :start
          and coalesce(s.saleDate, s.fechaCreacion) < :end
        order by coalesce(s.saleDate, s.fechaCreacion) desc
        """)
    List<Sale> findCashSalesByBusinessDateRange(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}

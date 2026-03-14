package com.gods.saas.domain.repository;


import com.gods.saas.domain.model.Sale;
import com.gods.saas.domain.repository.projection.BarberCommissionDailyProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SaleRepository extends JpaRepository<Sale, Long> {

    @Query("""
        select coalesce(sum(s.total), 0)
        from Sale s
        where s.tenant.id = :tenantId
          and s.user.id = :barberId
          and s.fechaCreacion between :start and :end
    """)
    BigDecimal sumTodaySales(Long tenantId, Long barberId, LocalDateTime start, LocalDateTime end);

    @Query("""
        select count(s)
        from Sale s
        where s.tenant.id = :tenantId
          and s.user.id = :barberId
          and s.fechaCreacion between :start and :end
    """)
    long countTodayServices(Long tenantId, Long barberId, LocalDateTime start, LocalDateTime end);


    List<Sale> findByTenant_IdAndBranch_IdAndUser_IdAndFechaCreacionBetween(
            Long tenantId,
            Long branchId,
            Long userId,
            LocalDateTime start,
            LocalDateTime end
    );

    @Query("""
        select
            cast(s.fechaCreacion as date) as fecha,
            coalesce(sum(s.total), 0) as ventas
        from Sale s
        where s.tenant.id = :tenantId
          and s.branch.id = :branchId
          and s.user.id = :barberId
          and s.fechaCreacion >= :start
          and s.fechaCreacion < :end
        group by cast(s.fechaCreacion as date)
        order by cast(s.fechaCreacion as date) asc
    """)
    List<BarberCommissionDailyProjection> findDailySalesByBarber(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId,
            @Param("barberId") Long barberId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}

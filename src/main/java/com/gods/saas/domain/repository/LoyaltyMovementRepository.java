package com.gods.saas.domain.repository;


import com.gods.saas.domain.model.LoyaltyMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoyaltyMovementRepository extends JpaRepository<LoyaltyMovement, Long> {
    List<LoyaltyMovement> findTop10ByTenantIdAndCustomerIdOrderByFechaCreacionDesc(Long tenantId, Long customerId);

    @Query(value = """
        select coalesce(sum(lm.puntos), 0)
        from loyalty_movement lm
        where lm.tenant_id = :tenantId
          and lm.customer_id = :customerId
          and lm.puntos > 0
          and date_trunc('month', lm.fecha_creacion) = date_trunc('month', current_timestamp)
        """, nativeQuery = true)

    Integer sumPositivePointsCurrentMonth(
            @Param("tenantId") Long tenantId,
            @Param("customerId") Long customerId
    );
}

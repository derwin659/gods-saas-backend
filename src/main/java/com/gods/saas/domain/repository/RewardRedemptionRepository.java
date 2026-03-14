package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.RewardRedemption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RewardRedemptionRepository extends JpaRepository<RewardRedemption, Long> {

    Optional<RewardRedemption> findByTenantIdAndCodigoIgnoreCase(Long tenantId, String codigo);

    Optional<RewardRedemption> findByCodigoIgnoreCase(String codigo);

    Optional<RewardRedemption> findByIdAndTenantId(Long id, Long tenantId);

    @Query("""
        select count(r)
        from RewardRedemption r
        where r.tenantId = :tenantId
          and r.customerId = :customerId
          and r.estado in ('GENERATED', 'USED')
    """)
    long countCompletedOrGeneratedRedemptions(
            @Param("tenantId") Long tenantId,
            @Param("customerId") Long customerId
    );
}
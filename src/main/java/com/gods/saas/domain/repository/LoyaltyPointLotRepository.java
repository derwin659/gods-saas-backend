package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.LoyaltyPointLot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface LoyaltyPointLotRepository extends JpaRepository<LoyaltyPointLot, Long> {

    List<LoyaltyPointLot> findByCustomerIdAndStatusOrderByExpiresAtAsc(Long customerId, String status);

    List<LoyaltyPointLot> findByStatusAndExpiresAtBefore(String status, LocalDateTime now);


    List<LoyaltyPointLot> findByTenantIdAndCustomerIdAndSourceTypeAndSourceReferenceIdOrderByEarnedAtAsc(
            Long tenantId,
            Long customerId,
            String sourceType,
            Long sourceReferenceId
    );
}

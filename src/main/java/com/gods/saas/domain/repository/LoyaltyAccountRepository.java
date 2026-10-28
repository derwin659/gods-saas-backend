package com.gods.saas.domain.repository;


import com.gods.saas.domain.model.LoyaltyAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LoyaltyAccountRepository extends JpaRepository<LoyaltyAccount, Long> {

    Optional<LoyaltyAccount> findByTenant_IdAndCustomer_Id(Long tenantId, Long customerId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select l from LoyaltyAccount l where l.tenant.id=:tenantId and l.customer.id=:customerId")
    Optional<LoyaltyAccount> findForRedemption(@org.springframework.data.repository.query.Param("tenantId") Long tenantId,
            @org.springframework.data.repository.query.Param("customerId") Long customerId);

    Optional<LoyaltyAccount> findByCustomer_Id(Long customerId);


}

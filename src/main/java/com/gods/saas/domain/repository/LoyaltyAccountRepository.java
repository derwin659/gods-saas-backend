package com.gods.saas.domain.repository;


import com.gods.saas.domain.model.LoyaltyAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LoyaltyAccountRepository extends JpaRepository<LoyaltyAccount, Long> {

    Optional<LoyaltyAccount> findByTenant_IdAndCustomer_Id(Long tenantId, Long customerId);


}

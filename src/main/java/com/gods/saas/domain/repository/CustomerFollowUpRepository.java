package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.CustomerFollowUp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerFollowUpRepository extends JpaRepository<CustomerFollowUp, Long> {
    List<CustomerFollowUp> findTop50ByTenant_IdAndCustomer_IdOrderByCreatedAtDesc(Long tenantId, Long customerId);
    Optional<CustomerFollowUp> findByIdAndTenant_IdAndCustomer_Id(Long id, Long tenantId, Long customerId);
}

package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.VerifiedBusinessReview;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface VerifiedBusinessReviewRepository extends JpaRepository<VerifiedBusinessReview, Long> {
    boolean existsByAppointment_Id(Long appointmentId);
    boolean existsBySale_Id(Long saleId);
    Optional<VerifiedBusinessReview> findByAppointment_IdAndCustomer_Id(Long appointmentId, Long customerId);
    long countByBranch_Id(Long branchId);
}
package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.VerifiedBusinessReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import java.util.List;

public interface VerifiedBusinessReviewRepository extends JpaRepository<VerifiedBusinessReview, Long> {
    boolean existsByAppointment_Id(Long appointmentId);
    boolean existsBySale_Id(Long saleId);
    Optional<VerifiedBusinessReview> findByAppointment_IdAndCustomer_Id(Long appointmentId, Long customerId);
    Optional<VerifiedBusinessReview> findByIdAndTenant_Id(Long reviewId, Long tenantId);
    long countByBranch_IdAndModerationStatusNot(Long branchId, String moderationStatus);
    List<VerifiedBusinessReview> findTop200ByTenant_IdOrderByCreatedAtDesc(Long tenantId);
    List<VerifiedBusinessReview> findTop20ByBranch_IdAndCommentIsNotNullOrderByCreatedAtDesc(Long branchId);
    List<VerifiedBusinessReview> findTop500ByOrderByCreatedAtDesc();
    List<VerifiedBusinessReview> findTop100ByTenant_IdAndCustomer_IdOrderByCreatedAtDesc(Long tenantId, Long customerId);
    List<VerifiedBusinessReview> findTop100ByTenant_IdOrderByCreatedAtDesc(Long tenantId);

    @Query("select avg(r.rating) from VerifiedBusinessReview r where r.branch.id = :branchId and r.moderationStatus <> 'HIDDEN'")
    Double findAverageRatingByBranchId(Long branchId);
}
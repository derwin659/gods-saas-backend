package com.gods.saas.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "verified_business_review", uniqueConstraints = {
        @UniqueConstraint(name = "uk_verified_review_appointment", columnNames = "appointment_id"),
        @UniqueConstraint(name = "uk_verified_review_sale", columnNames = "sale_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VerifiedBusinessReview {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "appointment_id")
    private Appointment appointment;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "sale_id")
    private Sale sale;
    @Column(nullable = false) private Integer rating;
    @Column(length = 500) private String comment;
    @Column(name = "owner_reply", length = 500) private String ownerReply;
    @Column(name = "owner_replied_at") private LocalDateTime ownerRepliedAt;
    @Column(name = "owner_replied_by_user_id") private Long ownerRepliedByUserId;
    @Builder.Default
    @Column(name = "moderation_status", nullable = false, length = 24)
    private String moderationStatus = "PUBLISHED";
    @Column(name = "report_reason", length = 40) private String reportReason;
    @Column(name = "report_details", length = 500) private String reportDetails;
    @Column(name = "reported_at") private LocalDateTime reportedAt;
    @Column(name = "reported_by_user_id") private Long reportedByUserId;
    @Column(name = "moderated_at") private LocalDateTime moderatedAt;
    @Column(name = "moderated_by_user_id") private Long moderatedByUserId;
    @Column(name = "moderation_note", length = 500) private String moderationNote;
    @Column(name = "created_at", nullable = false) @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
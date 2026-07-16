package com.gods.saas.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "verified_business_review", uniqueConstraints = @UniqueConstraint(name = "uk_verified_review_appointment", columnNames = "appointment_id"))
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
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;
    @Column(nullable = false) private Integer rating;
    @Column(length = 500) private String comment;
    @Column(name = "created_at", nullable = false) @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
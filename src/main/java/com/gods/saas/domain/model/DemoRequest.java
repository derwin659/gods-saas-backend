package com.gods.saas.domain.model;

import com.gods.saas.domain.enums.BusinessType;
import com.gods.saas.domain.enums.DemoRequestStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "demo_request")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DemoRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "demo_request_id")
    private Long id;

    @Column(name = "business_name", nullable = false, length = 150)
    private String businessName;

    @Enumerated(EnumType.STRING)
    @Column(name = "business_type", nullable = false, length = 50)
    private BusinessType businessType;

    @Column(name = "owner_name", nullable = false, length = 150)
    private String ownerName;

    @Column(name = "owner_email", nullable = false, length = 150)
    private String ownerEmail;

    @Column(name = "owner_phone", nullable = false, length = 30)
    private String ownerPhone;

    @Column(length = 80)
    private String country;

    @Column(length = 80)
    private String city;

    @Column(name = "branches_count")
    private Integer branchesCount;

    @Column(name = "professionals_count")
    private Integer professionalsCount;

    @Column(name = "social_link", length = 500)
    private String socialLink;

    @Column(name = "google_maps_link", length = 500)
    private String googleMapsLink;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DemoRequestStatus status;

    @Column(name = "review_notes", columnDefinition = "TEXT")
    private String reviewNotes;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "created_tenant_id")
    private Long createdTenantId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @PrePersist
    public void prePersist() {
        if (status == null) {
            status = DemoRequestStatus.PENDING_REVIEW;
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
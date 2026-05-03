package com.gods.saas.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "tenant_payment_method")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantPaymentMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_method_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    /**
     * Null = método global para todo el tenant.
     * Con branch = método solo para esa sede.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Column(name = "code", length = 50, nullable = false)
    private String code;

    @Column(name = "display_name", length = 100, nullable = false)
    private String displayName;

    @Column(name = "country_code", length = 10)
    private String countryCode;

    @Column(name = "instructions", columnDefinition = "TEXT")
    private String instructions;

    @Column(name = "account_label", length = 120)
    private String accountLabel;

    @Column(name = "account_value", length = 200)
    private String accountValue;

    @Column(name = "qr_image_url", columnDefinition = "TEXT")
    private String qrImageUrl;

    @Column(name = "requires_operation_code", nullable = false)
    @Builder.Default
    private Boolean requiresOperationCode = true;

    @Column(name = "requires_evidence", nullable = false)
    @Builder.Default
    private Boolean requiresEvidence = false;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (active == null) active = true;
        if (requiresOperationCode == null) requiresOperationCode = true;
        if (requiresEvidence == null) requiresEvidence = false;
        if (sortOrder == null) sortOrder = 0;
        if (code != null) code = code.trim().toUpperCase();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
        if (code != null) code = code.trim().toUpperCase();
    }
}
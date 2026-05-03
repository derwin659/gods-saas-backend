package com.gods.saas.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "appointment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "appointment_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "customer_id", nullable = true)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "user_id", nullable = true)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceEntity service;

    private LocalDate fecha;

    @Column(name = "hora_inicio")
    private LocalTime horaInicio;

    @Column(name = "hora_fin")
    private LocalTime horaFin;

    private String estado;
    private String notas;

    @Column(name = "total_amount", precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "deposit_required", nullable = false)
    @Builder.Default
    private Boolean depositRequired = false;

    @Column(name = "deposit_amount", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal depositAmount = BigDecimal.ZERO;

    @Column(name = "remaining_amount", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal remainingAmount = BigDecimal.ZERO;

    /**
     * NOT_REQUIRED / PENDING_VALIDATION / PAID / REJECTED
     */
    @Column(name = "deposit_status", length = 30, nullable = false)
    @Builder.Default
    private String depositStatus = "NOT_REQUIRED";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deposit_payment_method_id")
    private TenantPaymentMethod depositPaymentMethod;

    @Column(name = "deposit_method_code", length = 50)
    private String depositMethodCode;

    @Column(name = "deposit_method_name", length = 100)
    private String depositMethodName;

    @Column(name = "deposit_operation_code", length = 120)
    private String depositOperationCode;

    @Column(name = "deposit_evidence_url", columnDefinition = "TEXT")
    private String depositEvidenceUrl;

    @Column(name = "deposit_note", length = 500)
    private String depositNote;

    @Column(name = "deposit_paid_at")
    private LocalDateTime depositPaidAt;

    @Column(name = "deposit_validated_at")
    private LocalDateTime depositValidatedAt;

    @Column(name = "deposit_validated_by_user_id")
    private Long depositValidatedByUserId;

    @PrePersist
    @PreUpdate
    private void ensureDepositDefaults() {
        if (depositRequired == null) depositRequired = false;
        if (depositAmount == null) depositAmount = BigDecimal.ZERO;
        if (remainingAmount == null) remainingAmount = BigDecimal.ZERO;
        if (depositStatus == null || depositStatus.isBlank()) {
            depositStatus = Boolean.TRUE.equals(depositRequired) ? "PENDING_VALIDATION" : "NOT_REQUIRED";
        }
    }
}
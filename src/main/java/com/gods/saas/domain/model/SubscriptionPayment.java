package com.gods.saas.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "subscription_payments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "subscription_id")
    private Long subscriptionId;

    @Column(name = "requested_plan")
    private String requestedPlan;

    @Column(name = "billing_cycle")
    private String requestedBillingCycle;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "operation_number")
    private String operationNumber;

    private BigDecimal amount;

    @Column(name = "payer_name")
    private String payerName;

    @Column(name = "payer_phone")
    private String payerPhone;

    private String notes;
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "reviewed_by_user_id")
    private Long reviewedByUserId;

    @Column(name = "rejection_reason")
    private String rejectionReason;
}
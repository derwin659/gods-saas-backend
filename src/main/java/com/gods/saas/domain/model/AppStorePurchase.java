package com.gods.saas.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "app_store_purchase",
        indexes = {
                @Index(name = "idx_app_store_purchase_tenant", columnList = "tenant_id"),
                @Index(name = "idx_app_store_purchase_original_tx", columnList = "original_transaction_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppStorePurchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "subscription_id")
    private Long subscriptionId;

    @Column(name = "plan", nullable = false)
    private String plan;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "original_transaction_id")
    private String originalTransactionId;

    @Column(name = "app_account_token")
    private String appAccountToken;

    @Column(name = "environment")
    private String environment;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "purchased_at")
    private LocalDateTime purchasedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "receipt_data", columnDefinition = "TEXT")
    private String receiptData;

    @Column(name = "apple_response", columnDefinition = "TEXT")
    private String appleResponse;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

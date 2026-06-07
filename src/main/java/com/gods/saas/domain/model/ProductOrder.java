package com.gods.saas.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "product_order",
        indexes = {
                @Index(name = "idx_product_order_tenant_branch", columnList = "tenant_id, branch_id"),
                @Index(name = "idx_product_order_status", columnList = "status"),
                @Index(name = "idx_product_order_created", columnList = "created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_order_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "customer_name", length = 160, nullable = false)
    private String customerName;

    @Column(name = "customer_phone", length = 40, nullable = false)
    private String customerPhone;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", precision = 12, scale = 2, nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "total", precision = 12, scale = 2, nullable = false)
    private BigDecimal total;

    @Column(name = "payment_method", length = 40, nullable = false)
    private String paymentMethod;

    @Column(name = "payment_operation_number", length = 120)
    private String paymentOperationNumber;

    @Column(name = "payment_capture_url", length = 500)
    private String paymentCaptureUrl;

    @Column(name = "status", length = 40, nullable = false)
    private String status;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "admin_note", length = 500)
    private String adminNote;

    @Column(name = "sale_id")
    private Long saleId;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "validated_at")
    private LocalDateTime validatedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (status == null || status.isBlank()) status = "PENDING";
        if (paymentMethod == null || paymentMethod.isBlank()) paymentMethod = "PAY_AT_SHOP";
        if (quantity == null || quantity < 1) quantity = 1;
        if (unitPrice == null) unitPrice = BigDecimal.ZERO;
        if (total == null) total = unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

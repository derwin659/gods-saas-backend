package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ProductOrderResponse {
    private Long id;
    private Long tenantId;
    private Long branchId;
    private String branchName;
    private Long productId;
    private String productName;
    private String productImageUrl;
    private String customerName;
    private String customerPhone;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal total;
    private String paymentMethod;
    private String paymentOperationNumber;
    private String paymentCaptureUrl;
    private String status;
    private String statusLabel;
    private String notes;
    private String adminNote;
    private Long saleId;
    private String createdAt;
    private String updatedAt;
    private String validatedAt;
    private String deliveredAt;
    private String expiresAt;
}

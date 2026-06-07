package com.gods.saas.domain.dto.request;

import lombok.Data;

@Data
public class CreatePublicProductOrderRequest {
    private Long branchId;
    private Long productId;
    private String customerName;
    private String customerPhone;
    private Integer quantity;
    private String paymentMethod;
    private String paymentOperationNumber;
    private String paymentCaptureUrl;
    private String notes;
}

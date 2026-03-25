package com.gods.saas.domain.dto.request;

import lombok.Data;

@Data
public class ReportPaymentRequest {
    private String plan;
    private String billingCycle;   // MONTHLY / SEMIANNUAL / ANNUAL
    private String paymentMethod;  // YAPE / TRANSFER
    private String operationNumber;
    private Double amount;
    private String payerName;
    private String payerPhone;
    private String notes;
}
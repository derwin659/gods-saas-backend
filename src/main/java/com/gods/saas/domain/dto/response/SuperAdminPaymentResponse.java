package com.gods.saas.domain.dto.response;


import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class SuperAdminPaymentResponse {
    private Long paymentId;
    private Long tenantId;
    private String businessName;
    private String plan;
    private String billingCycle;
    private BigDecimal amount;
    private String currency;
    private String operationNumber;
    private String payerName;
    private String status;
    private LocalDateTime createdAt;
}

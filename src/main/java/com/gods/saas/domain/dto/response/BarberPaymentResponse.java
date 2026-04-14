package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class BarberPaymentResponse {
    private BigDecimal salaryAmount;
    private Long paymentId;
    private Long barberUserId;
    private String barberName;
    private String paymentMode;
    private String status;
    private LocalDate periodFrom;
    private LocalDate periodTo;
    private BigDecimal baseAmount;
    private BigDecimal percentageApplied;
    private BigDecimal commissionAmount;
    private BigDecimal advancesApplied;
    private BigDecimal previousPaymentsApplied;
    private BigDecimal amountPaid;
    private BigDecimal remainingAmount;
    private String paymentMethod;
    private Long cashMovementId;
    private String concept;
    private String note;
    private LocalDateTime createdAt;
}
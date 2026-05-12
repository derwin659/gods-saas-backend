package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class BarberPaymentPreviewResponse {
    private Long barberUserId;
    private String barberName;
    private String paymentMode;
    private LocalDate periodFrom;
    private LocalDate periodTo;

    private BigDecimal baseSales;
    private BigDecimal percentageApplied;
    private BigDecimal serviceCommissionAmount;
    private BigDecimal commissionAmount;
    private BigDecimal productCommissionAmount;

    private BigDecimal salaryAmount;
    private BigDecimal tipsAmount;
    private BigDecimal grossAmount;

    private BigDecimal advancesApplied;
    private BigDecimal previousPaymentsApplied;
    private BigDecimal pendingAmount;
}

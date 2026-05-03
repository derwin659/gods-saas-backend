package com.gods.saas.domain.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class UpdateBookingPaymentSettingsRequest {
    private Boolean bookingDepositEnabled;
    private String bookingDepositMode; // FIXED / PERCENT
    private BigDecimal bookingDepositDefaultAmount;
    private Integer bookingDepositDefaultPercent;

    private List<PaymentMethodConfigRequest> paymentMethods;
}
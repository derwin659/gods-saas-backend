package com.gods.saas.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingPaymentSettingsResponse {
    private Boolean bookingDepositEnabled;
    private String bookingDepositMode; // FIXED / PERCENT
    private BigDecimal bookingDepositDefaultAmount;
    private Integer bookingDepositDefaultPercent;

    private List<PaymentMethodConfigResponse> paymentMethods;
}
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
public class BookingBootstrapResponse {
    private List<BranchMiniResponse> branches;
    private List<ServiceMiniResponse> services;
    private List<BarberMiniResponse> barbers;

    private Boolean bookingDepositEnabled;
    private BigDecimal bookingDepositDefaultAmount;
    private Integer bookingDepositDefaultPercent;

    private List<PaymentMethodMiniResponse> paymentMethods;
}
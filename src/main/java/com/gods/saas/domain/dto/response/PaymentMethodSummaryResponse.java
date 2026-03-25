package com.gods.saas.domain.dto.response;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentMethodSummaryResponse {
    private String paymentMethod;
    private Long count;
    private BigDecimal totalAmount;
}

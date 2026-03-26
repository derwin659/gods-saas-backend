package com.gods.saas.domain.dto.response;


import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PaymentSummaryResponse {
    private BigDecimal cash;
    private BigDecimal yape;
    private BigDecimal plin;
    private BigDecimal free;
    private BigDecimal card;
    private BigDecimal transfer;
    private BigDecimal total;
}
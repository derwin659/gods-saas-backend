package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class SalePaymentResponse {
    private Long id;
    private String method;
    private BigDecimal amount;
}

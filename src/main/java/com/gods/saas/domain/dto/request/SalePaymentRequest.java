package com.gods.saas.domain.dto.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SalePaymentRequest {
    /**
     * CASH / EFECTIVO, YAPE, PLIN, CARD / TARJETA, TRANSFER / TRANSFERENCIA, FREE / GRATIS
     */
    private String method;
    private BigDecimal amount;
}

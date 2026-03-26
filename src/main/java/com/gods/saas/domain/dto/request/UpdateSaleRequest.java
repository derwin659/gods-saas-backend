package com.gods.saas.domain.dto.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateSaleRequest {
    private Long customerId;
    private String metodoPago;
    private BigDecimal subtotal;
    private BigDecimal discount;
    private BigDecimal total;
    private BigDecimal cashReceived;
    private BigDecimal changeAmount;
}
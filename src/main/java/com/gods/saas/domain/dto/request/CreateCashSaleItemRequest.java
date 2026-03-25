package com.gods.saas.domain.dto.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateCashSaleItemRequest {

    private Long serviceId;
    private Long productId;
    private Long barberUserId;
    private Integer cantidad;
    private BigDecimal precioUnitario;
}

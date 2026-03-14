package com.gods.saas.domain.dto.request;


import lombok.Data;

import java.math.BigDecimal;

@Data
public class AttendCheckoutItemRequest {
    private Long serviceId;
    private Integer quantity;
    private BigDecimal unitPrice;
}

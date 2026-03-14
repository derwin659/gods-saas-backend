package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class AttendCheckoutResponse {
    private Long saleId;
    private String message;
    private BigDecimal total;
    private String paymentMethod;
    private Integer pointsEarned;
    private Integer pointsAvailable;
}

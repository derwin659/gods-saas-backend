package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class TopServiceResponse {
    private String serviceName;
    private Long timesSold;
    private BigDecimal totalAmount;
}
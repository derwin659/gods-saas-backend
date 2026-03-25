package com.gods.saas.domain.dto.request;


import lombok.Data;

import java.math.BigDecimal;

@Data
public class ChangePlancRequest {
    private String plan;
    private String billingCycle;
    private BigDecimal price;
    private String currency;
    private String observations;
}
package com.gods.saas.domain.dto.request;


import lombok.Data;

import java.math.BigDecimal;

@Data
public class OpenCashRegisterRequest {
    private Long assignedUserId;
    private BigDecimal openingAmount;
    private String openingNote;
}
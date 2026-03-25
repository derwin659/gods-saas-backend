package com.gods.saas.domain.dto.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CloseCashRegisterRequest {
    private BigDecimal closingAmountCounted;
    private String closingNote;
}
package com.gods.saas.domain.dto.request;

import com.gods.saas.domain.enums.PaymentMethod;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class ReconcileCashRegisterRequest {
    private BigDecimal closingAmountCounted;
    private Map<PaymentMethod, BigDecimal> fundDeposits;
    private String note;
}
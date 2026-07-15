package com.gods.saas.domain.dto.request;

import com.gods.saas.domain.enums.CashFundMovementType;
import com.gods.saas.domain.enums.PaymentMethod;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CashFundMovementRequest {
    private CashFundMovementType type;
    private PaymentMethod paymentMethod;
    private BigDecimal amount;
    private String concept;
    private String note;
    private LocalDate movementDate;
}
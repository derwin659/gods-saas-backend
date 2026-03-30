package com.gods.saas.domain.dto.request;

import com.gods.saas.domain.enums.CashMovementType;
import com.gods.saas.domain.enums.PaymentMethod;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CashMovementRequest {
    private CashMovementType type;
    private PaymentMethod paymentMethod;
    private BigDecimal amount;
    private String concept;
    private String note;
    private Long barberUserId;
}

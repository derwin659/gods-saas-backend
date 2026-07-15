package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class CashFundMovementResponse {
    private Long id;
    private String type;
    private String paymentMethod;
    private BigDecimal amount;
    private BigDecimal signedAmount;
    private String concept;
    private String note;
    private LocalDateTime movementDate;
    private Long cashRegisterId;
    private Long actorUserId;
    private String actorUserName;
}
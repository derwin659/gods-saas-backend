package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class CashMovementResponse {
    private Long id;
    private String type;
    private String paymentMethod;
    private BigDecimal amount;
    private String concept;
    private String note;
    private LocalDateTime movementDate;
    private Long userId;
    private String userName;
    private Long barberUserId;
    private String barberUserName;
}

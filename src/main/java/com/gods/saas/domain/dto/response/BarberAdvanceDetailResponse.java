package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class BarberAdvanceDetailResponse {

    private Long movementId;

    private LocalDateTime movementDate;

    private BigDecimal amount;

    private String concept;

    private String note;

    private String paymentMethod;
}
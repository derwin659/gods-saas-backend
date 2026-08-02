package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class CashFundRangeSummaryResponse {
    private Long branchId;
    private String branchName;
    private LocalDate from;
    private LocalDate to;
    private BigDecimal openingBalance;
    private BigDecimal totalIn;
    private BigDecimal totalOut;
    private BigDecimal netMovement;
    private BigDecimal closingBalance;
    private BigDecimal currentBalance;
}

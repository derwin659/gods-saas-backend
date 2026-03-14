package com.gods.saas.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommissionSummaryResponse {

    private String scheme;
    private BigDecimal percentage;
    private BigDecimal baseAmountToday;
    private BigDecimal commissionToday;
    private boolean salaryMode;
}

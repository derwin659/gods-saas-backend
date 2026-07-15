package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class CashFundSummaryResponse {
    private Long branchId;
    private String branchName;
    private BigDecimal totalBalance;
    private List<PaymentMethodSummaryResponse> balances;
}
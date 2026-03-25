package com.gods.saas.domain.dto.response;


import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class BranchSummaryResponse {
    private BigDecimal totalSales;
    private Long totalSalesCount;
    private BigDecimal averageTicket;
    private Long activeBarbers;
}
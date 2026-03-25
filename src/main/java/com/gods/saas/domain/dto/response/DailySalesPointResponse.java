package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class DailySalesPointResponse {
    private LocalDate date;
    private BigDecimal totalSales;
    private Long salesCount;
}
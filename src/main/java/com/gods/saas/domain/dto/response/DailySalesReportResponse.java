package com.gods.saas.domain.dto.response;


import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailySalesReportResponse {
    private LocalDate date;
    private BigDecimal totalSales;
}

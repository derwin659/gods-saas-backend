package com.gods.saas.domain.dto.response;


import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopServiceReportResponse {
    private Long serviceId;
    private String serviceName;
    private Long quantity;
    private BigDecimal totalAmount;
}
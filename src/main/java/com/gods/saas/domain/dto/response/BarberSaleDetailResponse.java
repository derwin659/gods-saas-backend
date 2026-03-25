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
public class BarberSaleDetailResponse {
    private Long saleId;
    private String customerName;
    private String serviceNames;
    private BigDecimal total;
    private String paymentMethod;
    private String createdAt;
}
package com.gods.saas.domain.dto.request;


import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class CreateSaleRequest {
    private Long tenantId;
    private Long branchId;
    private Long customerId;
    private Long userId;
    private BigDecimal cashReceived;
    private Long appointmentId;
    private String metodoPago;
    private Double total;
    private List<SaleItemRequest> items;
    private BigDecimal discount;

}

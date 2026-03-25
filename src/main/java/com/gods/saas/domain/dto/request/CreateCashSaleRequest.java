package com.gods.saas.domain.dto.request;


import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateCashSaleRequest {

    private Long customerId;
    private Long appointmentId;
    private String metodoPago;
    private BigDecimal discount;
    private BigDecimal cashReceived;
    private List<CreateCashSaleItemRequest> items;

}
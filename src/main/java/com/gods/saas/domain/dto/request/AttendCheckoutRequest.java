package com.gods.saas.domain.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class AttendCheckoutRequest {

    private Long tenantId;
    private Long branchId;

    private Long customerId; // opcional
    private Long barberId;   // obligatorio

    private String paymentMethod; // CASH, CARD, TRANSFER
    private BigDecimal cashReceived; // solo si es CASH
    private BigDecimal changeAmount; // solo si es CASH

    private BigDecimal subtotal;
    private BigDecimal discount;
    private BigDecimal total;

    private List<AttendCheckoutItemRequest> items;
}
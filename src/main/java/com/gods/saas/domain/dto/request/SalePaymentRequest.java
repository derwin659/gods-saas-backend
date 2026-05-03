package com.gods.saas.domain.dto.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SalePaymentRequest {
    /**
     * Ejemplos:
     * EFECTIVO / CASH
     * TARJETA / CARD
     * YAPE
     * PLIN
     * TRANSFER
     * NEQUI
     * DAVIPLATA
     * PAGO_MOVIL
     * ZELLE
     * QR
     * FREE
     * DEPOSIT_APPLIED = inicial de reserva ya aprobado
     */
    private String method;

    private BigDecimal amount;
}
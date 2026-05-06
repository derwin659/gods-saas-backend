package com.gods.saas.domain.dto.request;

import com.gods.saas.domain.enums.CashMovementType;
import com.gods.saas.domain.enums.PaymentMethod;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CashMovementRequest {
    private CashMovementType type;

    /**
     * Para INCOME, EXPENSE, ADVANCE_BARBER y PAYMENT_BARBER.
     */
    private PaymentMethod paymentMethod;

    /**
     * Para PAYMENT_METHOD_TRANSFER.
     */
    private PaymentMethod fromPaymentMethod;
    private PaymentMethod toPaymentMethod;

    private BigDecimal amount;
    private String concept;
    private String note;
    private Long barberUserId;

    /**
     * Fecha contable del movimiento.
     * Si viene null, se usa la fecha actual del tenant.
     * Ejemplo: "2026-05-02"
     */
    private LocalDate movementDate;
}
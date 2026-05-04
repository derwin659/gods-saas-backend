package com.gods.saas.domain.enums;

public enum CashMovementType {
    INCOME,
    EXPENSE,
    ADVANCE_BARBER,
    PAYMENT_BARBER,

    /**
     * Traslado interno entre métodos de pago.
     * Ejemplo: YAPE -> CASH o CASH -> TRANSFER.
     * No crea venta, no crea gasto, no crea ingreso nuevo.
     */
    PAYMENT_METHOD_TRANSFER,

    ADJUSTMENT
}

package com.gods.saas.domain.dto.request;


import com.gods.saas.domain.enums.PaymentMethod;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.time.LocalDate;

@Data
public class CreateBarberPaymentRequest {
    private Long barberUserId;
    private LocalDate periodFrom;
    private LocalDate periodTo;
    /**
     * Modo antiguo: un solo método de pago.
     * Se mantiene para no romper web/app actuales.
     */
    private BigDecimal amountPaid;
    private PaymentMethod paymentMethod;

    /**
     * Modo nuevo: pago mixto.
     * Ejemplo: [{ method: "YAPE", amount: 200 }, { method: "CASH", amount: 135 }]
     */
    private List<BarberPaymentSplitRequest> payments;

    private String note;

    @Data
    public static class BarberPaymentSplitRequest {
        private String method;
        private BigDecimal amount;
    }
}
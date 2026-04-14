package com.gods.saas.domain.dto.request;


import com.gods.saas.domain.enums.PaymentMethod;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CreateBarberPaymentRequest {
    private Long barberUserId;
    private LocalDate periodFrom;
    private LocalDate periodTo;
    private BigDecimal amountPaid;
    private PaymentMethod paymentMethod;
    private String note;
}
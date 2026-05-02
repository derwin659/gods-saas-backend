package com.gods.saas.domain.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class CreateSaleFromAppointmentRequest {
    private Long appointmentId;
    private String metodoPago;
    private Double cashReceived;
    private String notes;

    private BigDecimal tipAmount;
    private List<SalePaymentRequest> payments;

    /** Productos adicionales vendidos por el barbero al finalizar la cita. */
    private List<SaleItemRequest> items;

    private String cutType;
    private String cutDetail;
    private String cutObservations;
}

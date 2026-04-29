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

    /** Propina que se suma al total cobrado y al pago pendiente del barbero. */
    private BigDecimal tipAmount;

    /** Barbero que recibirá la propina. Si viene null, se usa el primer barbero de los items. */
    private Long tipBarberUserId;

    /** Pagos mixtos: efectivo + yape + plin + tarjeta, etc. */
    private List<SalePaymentRequest> payments;

    private String cutType;
    private String cutDetail;
    private String cutObservations;
}

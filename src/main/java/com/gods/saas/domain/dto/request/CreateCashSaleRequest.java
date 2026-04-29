package com.gods.saas.domain.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CreateCashSaleRequest {

    private Long customerId;
    private Long appointmentId;
    private String metodoPago;
    private BigDecimal discount;
    private BigDecimal cashReceived;

    /** Propina para el barbero. */
    private BigDecimal tipAmount;

    /** Barbero que recibirá la propina. Si viene null, se asigna al primer barbero de la venta. */
    private Long tipBarberUserId;

    /** Pagos mixtos: CASH + YAPE + PLIN + CARD + TRANSFER. */
    private List<SalePaymentRequest> payments;

    /**
     * Fecha/hora real en la que ocurrió la venta.
     * Si viene null, se usa la fecha/hora actual del tenant.
     * Ejemplo desde Flutter: 2026-04-27T18:30:00
     */
    private LocalDateTime saleDate;

    private List<CreateCashSaleItemRequest> items;

    private String cutType;
    private String cutDetail;
    private String cutObservations;
}

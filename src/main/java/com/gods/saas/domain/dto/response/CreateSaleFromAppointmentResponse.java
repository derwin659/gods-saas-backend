package com.gods.saas.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class CreateSaleFromAppointmentResponse {
    private String status;
    private boolean success;
    private String message;

    private Long saleId;
    private Long appointmentId;

    private String cliente;
    private String servicio;
    private String metodoPago;

    /**
     * Total real de la venta:
     * servicios + productos + propina - descuento.
     */
    private Double total;

    /**
     * Inicial aprobado de la reserva.
     */
    private Double depositApplied;

    /**
     * Saldo que se cobró en caja al finalizar atención.
     */
    private Double amountToCollectNow;

    private String depositMethodCode;
    private String depositMethodName;
    private String depositStatus;

    private Double cashReceived;
    private Double change;

    private Integer pointsEarned;
    private Integer customerPointsBalance;

    private String fechaHora;

    public CreateSaleFromAppointmentResponse() {
    }
}
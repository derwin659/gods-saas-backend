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
    private Double total;
    private Double cashReceived;
    private Double change;
    private Integer pointsEarned;
    private Integer customerPointsBalance;
    private String fechaHora;

    public CreateSaleFromAppointmentResponse() {

    }
}

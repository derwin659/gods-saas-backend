package com.gods.saas.domain.dto.request;


import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateSaleFromAppointmentRequest {
    private Long appointmentId;
    private String metodoPago;
    private Double cashReceived;
    private String notes;
}

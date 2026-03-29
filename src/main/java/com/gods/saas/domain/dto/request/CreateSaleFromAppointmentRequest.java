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

    // NUEVO
    private String cutType;          // Fade, Clasico, Buzz Cut, Taper, etc.
    private String cutDetail;        // Low Fade, Mid Fade, High Fade, etc.
    private String cutObservations;  // Dejar volumen arriba, no tocar línea frontal...
}
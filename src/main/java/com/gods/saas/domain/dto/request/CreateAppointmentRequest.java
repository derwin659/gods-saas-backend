package com.gods.saas.domain.dto.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateAppointmentRequest {
    private Long branchId;
    private Long serviceId;
    private Long barberId;     // null = cualquiera
    private String date;       // yyyy-MM-dd
    private String horaInicio; // HH:mm

    /**
     * Promoción seleccionada desde la app del cliente.
     * Si viene null, la reserva se crea sin promoción.
     */
    private Long promotionId;

    /**
     * Si el cliente confirma con inicial.
     */
    private Boolean depositRequired;

    /**
     * Método configurado por el dueño.
     */
    private Long depositPaymentMethodId;

    /**
     * Monto que el cliente pagó como inicial.
     */
    private BigDecimal depositAmount;

    /**
     * Número operación / referencia / comprobante.
     */
    private String depositOperationCode;

    /**
     * Imagen futura del comprobante. Por ahora puede ir null.
     */
    private String depositEvidenceUrl;

    private String depositNote;
}

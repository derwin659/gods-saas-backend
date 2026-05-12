package com.gods.saas.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerAgendaResponse {

    private Long appointmentId;
    private Long customerId;
    private Long serviceId;
    private Long barberUserId;
    private Long branchId;

    private String fecha;
    private String hora;
    private String horaFin;

    private String cliente;
    private String servicio;
    private String barbero;
    private String estado;
    private String telefono;

    // =========================
    // Promoción / importes
    // =========================
    private String promotionTitle;
    private BigDecimal originalAmount;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;

    // =========================
    // Pago inicial / reserva
    // =========================
    private Boolean requierePagoInicial;
    private BigDecimal montoPagoInicial;
    private BigDecimal precioServicio;
    private BigDecimal saldoPendiente;

    private String metodoPagoInicial;
    private String numeroOperacionPagoInicial;
    private String comprobantePagoInicialUrl;

    /**
     * Estados sugeridos:
     * PENDIENTE_VALIDACION
     * VALIDADO
     * RECHAZADO
     * NO_REQUIERE
     */
    private String estadoPagoInicial;

    private Boolean pagoInicialValidado;
}
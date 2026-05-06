package com.gods.saas.domain.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOwnerAppointmentRequest {
    private Long customerId;
    private Long serviceId;
    private Long barberUserId;
    private String fecha;       // yyyy-MM-dd
    private String horaInicio;  // HH:mm
    private String horaFin;     // HH:mm opcional
    private String estado;
    private String notas;

    private Boolean depositRequired;
    private BigDecimal depositAmount;
    private String depositMethodCode;
    private String depositMethodName;
    private String depositOperationCode;
    private String depositEvidenceUrl;
    private String depositNote;
}

package com.gods.saas.domain.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PublicCreateAppointmentRequest {

    private Long branchId;
    private Long serviceId;
    private List<Long> serviceIds;
    private Long barberId;

    private String date;
    private String horaInicio;

    private String customerName;
    private String customerLastName;
    private String customerPhone;
    private String customerEmail;

    private Long promotionId;

    private Boolean depositRequired;
    private Long depositPaymentMethodId;
    private BigDecimal depositAmount;
    private String depositOperationCode;
    private String depositEvidenceUrl;
    private String depositNote;
}

package com.gods.saas.domain.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SuperAdminUpdateTenantRequest {
    private String businessName;
    private String businessType;
    private String ownerName;
    private String ownerEmail;
    private String ownerPhone;
    private String country;
    private String plan;
    private String billingCycle;
    private String currency;
    private BigDecimal price;
    private String status;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private String observations;
}

package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SuperAdminTenantResponse {
    private Long tenantId;
    private String businessName;
    private String businessType;
    private String country;
    private String ownerName;
    private String ownerEmail;
    private String ownerPhone;
    private String code;
    private String plan;
    private Double price;
    private String currency;
    private String status;
    private String rawStatus;
    private Boolean tenantActive;
    private Boolean trial;
    private Long daysRemaining;
    private String billingCycle;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
}

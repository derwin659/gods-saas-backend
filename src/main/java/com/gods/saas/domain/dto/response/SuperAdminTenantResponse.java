package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SuperAdminTenantResponse {
    private Long tenantId;
    private String businessName;
    private String ownerName;
    private String ownerEmail;
    private String code;
    private String plan;
    private String status;
    private String billingCycle;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
}
package com.gods.saas.domain.dto.request;

import lombok.Data;

@Data
public class SuperAdminCreateTenantRequest {
    private String businessName;
    private String ownerName;
    private String ownerEmail;
    private String ownerPhone;
    private String plan;          // STARTER, PRO, GODS_AI
    private String billingCycle;  // MONTHLY, SEMIANNUAL, YEARLY
    private Integer trialDays;
    private String currency;      // USD, PEN
    private String branchName;
    private String branchAddress;
    private String branchPhone;
}
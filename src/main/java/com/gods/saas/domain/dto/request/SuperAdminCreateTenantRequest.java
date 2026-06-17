package com.gods.saas.domain.dto.request;

import com.gods.saas.domain.enums.BusinessType;
import lombok.Data;

@Data
public class SuperAdminCreateTenantRequest {
    private String businessName;
    private BusinessType businessType;

    private String ownerName;
    private String ownerEmail;
    private String ownerPhone;
    private String country;

    private String plan;          // FREE, BASIC, STARTER, GROWTH, PRO, ENTERPRISE, *_LEGACY
    private String billingCycle;  // MONTHLY, SEMIANNUAL, YEARLY
    private Integer trialDays;
    private String currency;      // USD, PEN

    private String branchName;
    private String branchAddress;
    private String branchPhone;
}

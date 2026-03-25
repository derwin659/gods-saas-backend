package com.gods.saas.domain.dto.response;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SuperAdminDashboardResponse {
    private long totalTenants;
    private long activeTenants;
    private long trialTenants;
    private long expiredTenants;
    private long suspendedTenants;
    private long pendingPayments;
}
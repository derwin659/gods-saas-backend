package com.gods.saas.domain.dto;

import com.gods.saas.domain.model.Tenant;
import lombok.Data;

import java.util.List;

@Data
public class AdminDashboardDTO {
    private long totalTenants;
    private long activeTenants;
    private long inactiveTenants;
    private double incomeThisMonth;
    private List<Tenant> recentTenants;
}


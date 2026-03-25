package com.gods.saas.service.impl.impl;


import com.gods.saas.domain.dto.response.OwnerHomeDashboardResponse;

public interface OwnerHomeDashboardService  {
    OwnerHomeDashboardResponse getDashboard(Long tenantId, Long branchId);
}
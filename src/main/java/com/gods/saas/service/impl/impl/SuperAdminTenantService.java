package com.gods.saas.service.impl.impl;

import com.gods.saas.domain.dto.request.ChangePlanRequest;
import com.gods.saas.domain.dto.request.ChangePlancRequest;
import com.gods.saas.domain.dto.request.SuperAdminCreateTenantRequest;
import com.gods.saas.domain.dto.response.SuperAdminDashboardResponse;

import com.gods.saas.domain.dto.response.SuperAdminTenantResponse;

import java.util.List;

public interface SuperAdminTenantService {
    List<SuperAdminTenantResponse> findAll();
    SuperAdminTenantResponse findById(Long tenantId);
    SuperAdminTenantResponse create(SuperAdminCreateTenantRequest request);
    void activate(Long tenantId);
    void suspend(Long tenantId);
    void changePlan(Long tenantId, ChangePlancRequest request);
    SuperAdminDashboardResponse dashboard();
}
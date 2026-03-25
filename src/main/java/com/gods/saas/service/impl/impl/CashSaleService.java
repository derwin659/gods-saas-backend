package com.gods.saas.service.impl.impl;

import com.gods.saas.domain.dto.request.CreateCashSaleRequest;
import com.gods.saas.domain.dto.response.SaleResponse;

import java.time.LocalDate;
import java.util.List;

public interface CashSaleService {

    SaleResponse createCashSale(Long tenantId, Long branchId, Long userId, CreateCashSaleRequest request);

    List<SaleResponse> getTodaySales(Long tenantId, Long branchId);

    List<SaleResponse> getSalesByRange(Long tenantId, Long branchId, LocalDate from, LocalDate to);

    SaleResponse getById(Long tenantId, Long saleId);
}
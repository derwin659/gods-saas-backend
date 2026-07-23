package com.gods.saas.service.impl.impl;

import com.gods.saas.domain.dto.request.CreateCashSaleRequest;
import com.gods.saas.domain.dto.request.UpdateSaleRequest;
import com.gods.saas.domain.dto.request.PrinterEventRequest;
import com.gods.saas.domain.dto.response.SaleResponse;

import java.time.LocalDate;
import java.util.List;

public interface CashSaleService {

    SaleResponse createCashSale(Long tenantId, Long branchId, Long userId, CreateCashSaleRequest request);

    List<SaleResponse> getTodaySales(Long tenantId, Long branchId);

    List<SaleResponse> getSalesByRange(Long tenantId, Long branchId, LocalDate from, LocalDate to);

    List<SaleResponse> getSalesByCashRegister(Long tenantId, Long branchId, Long cashRegisterId);

    SaleResponse getById(Long tenantId, Long saleId);

    SaleResponse updateSale(Long tenantId, Long branchId, Long userId, Long saleId, UpdateSaleRequest request);

    List<SaleResponse> getPendingValidationSales(Long tenantId, Long branchId);

    SaleResponse approveSalePayment(Long tenantId, Long branchId, Long userId, Long saleId);

    SaleResponse rejectSalePayment(Long tenantId, Long branchId, Long userId, Long saleId, String reason);

    void deleteSale(Long tenantId, Long branchId, Long userId, Long saleId, String auditReason);

    void registerPrinterEvent(Long tenantId, Long branchId, Long userId, Long saleId, PrinterEventRequest request);

    void registerDrawerEvent(Long tenantId, Long branchId, Long userId, Long saleId, PrinterEventRequest request);
}

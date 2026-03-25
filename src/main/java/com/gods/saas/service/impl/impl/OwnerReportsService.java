package com.gods.saas.service.impl.impl;

import com.gods.saas.domain.dto.response.*;
import java.time.LocalDate;
import java.util.List;

public interface OwnerReportsService {

    OwnerSalesReportResponse getSalesReport(
            Long tenantId,
            Long branchId,
            LocalDate from,
            LocalDate to
    );

    List<BarberSaleDetailResponse> getBarberSalesDetail(
            Long tenantId,
            Long branchId,
            Long barberId,
            LocalDate from,
            LocalDate to
    );

    BranchSummaryResponse getBranchSummary(
            Long tenantId,
            LocalDate from,
            LocalDate to
    );

    BranchDetailResponse getBranchDetail(
            Long tenantId,
            Long branchId,
            LocalDate from,
            LocalDate to
    );

    List<BarberSalesSummaryResponse> getBranchBarbersReport(
            Long tenantId,
            Long branchId,
            LocalDate from,
            LocalDate to
    );

    List<BarberSalesSummaryResponse> getBarberSummary(
            Long tenantId,
            Long branchId,
            LocalDate from,
            LocalDate to
    );

    List<BarberSaleDetailResponse> getBarberDetail(
            Long tenantId,
            Long branchId,
            Long barberId,
            LocalDate from,
            LocalDate to
    );

    List<DailySalesPointResponse> getDailySales(
            Long tenantId,
            Long branchId,
            LocalDate from,
            LocalDate to
    );

    List<TopServiceResponse> getTopServices(
            Long tenantId,
            Long branchId,
            LocalDate from,
            LocalDate to
    );

    PaymentSummaryResponse getPaymentSummary(
            Long tenantId,
            Long branchId,
            LocalDate from,
            LocalDate to
    );
}
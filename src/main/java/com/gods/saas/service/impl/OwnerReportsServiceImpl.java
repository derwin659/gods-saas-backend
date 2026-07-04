package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.response.*;

import com.gods.saas.domain.model.Subscription;
import com.gods.saas.domain.model.CashMovement;
import com.gods.saas.domain.model.BarberPayment;
import com.gods.saas.domain.enums.BarberPaymentMode;
import com.gods.saas.domain.enums.BarberPaymentStatus;
import com.gods.saas.domain.repository.BarberPaymentRepository;
import com.gods.saas.domain.enums.CashMovementType;
import com.gods.saas.domain.repository.CashMovementRepository;
import com.gods.saas.domain.repository.SaleRepository;
import com.gods.saas.domain.repository.SubscriptionRepository;
import com.gods.saas.domain.repository.projection.BarberSaleDetailProjection;
import com.gods.saas.domain.repository.projection.BarberSalesSummaryProjection;
import com.gods.saas.service.impl.impl.OwnerReportsService;
import com.gods.saas.service.impl.impl.BarberPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

@Service
@RequiredArgsConstructor
public class OwnerReportsServiceImpl implements OwnerReportsService {

    private final SaleRepository saleRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final CashMovementRepository cashMovementRepository;
    private final BarberPaymentRepository barberPaymentRepository;
    private final BarberPaymentService barberPaymentService;

    @Override
    public ProfitabilityReportResponse getProfitabilityReport(
            Long tenantId,
            Long branchId,
            LocalDate from,
            LocalDate to
    ) {
        validateAdvancedReportsAllowed(tenantId);

        LocalDateTime start = startOfDay(from);
        LocalDateTime end = endInclusive(to);

        BigDecimal totalSales = nvl(saleRepository.getTotalSalesByRange(tenantId, branchId, start, end));
        BigDecimal cashSales = nvl(saleRepository.getCashSalesByRange(tenantId, branchId, start, end));
        BigDecimal nonCashSales = totalSales.subtract(cashSales);

        BigDecimal operationalExpenses = nvl(
                cashMovementRepository.sumGeneralExpensesByRange(tenantId, branchId, start, end)
        );

        BigDecimal additionalIncome = nvl(
                cashMovementRepository.sumAdditionalIncomeByRange(tenantId, branchId, start, end)
        );

        BigDecimal barberAdvances = nvl(
                cashMovementRepository.sumBarberAdvancesByRange(tenantId, branchId, start, end)
        );

        BigDecimal barberPayments = nvl(
                cashMovementRepository.sumBarberPaymentsByRange(tenantId, branchId, start, end)
        );

        BigDecimal grossIncome = totalSales.add(additionalIncome);
        BigDecimal barberCommissionsAccrued = nvl(
                saleRepository.sumAccruedBarberCommissionsByRange(tenantId, branchId, start, end)
        );

        // Utilidad por devengo: los pagos y adelantos liquidan una deuda, no son un gasto adicional.
        BigDecimal netProfit = grossIncome
                .subtract(operationalExpenses)
                .subtract(barberCommissionsAccrued);
        BigDecimal cashFlowAfterBarberSettlements = grossIncome
                .subtract(operationalExpenses)
                .subtract(barberAdvances)
                .subtract(barberPayments);

        BigDecimal profitMargin = BigDecimal.ZERO;
        if (grossIncome.compareTo(BigDecimal.ZERO) > 0) {
            profitMargin = netProfit
                    .multiply(new BigDecimal("100"))
                    .divide(grossIncome, 2, RoundingMode.HALF_UP);
        }

        List<DailyProfitabilityPointResponse> daily = cashMovementRepository
                .getDailyProfitability(tenantId, branchId, start, end, to)
                .stream()
                .map(p -> {
                    BigDecimal dailySales = nvl(p.getTotalSales());
                    BigDecimal dailyAdditionalIncome = nvl(p.getAdditionalIncome());
                    BigDecimal dailyExpenses = nvl(p.getOperationalExpenses());
                    BigDecimal dailyAdvances = nvl(p.getBarberAdvances());
                    BigDecimal dailyPayments = nvl(p.getBarberPayments());

                    LocalDateTime dailyStart = p.getReportDate().atStartOfDay();
                    LocalDateTime dailyEnd = p.getReportDate().plusDays(1).atStartOfDay();
                    BigDecimal dailyCommissionsAccrued = nvl(
                            saleRepository.sumAccruedBarberCommissionsByRange(
                                    tenantId, branchId, dailyStart, dailyEnd
                            )
                    );
                    BigDecimal dailyProfit = dailySales
                            .add(dailyAdditionalIncome)
                            .subtract(dailyExpenses)
                            .subtract(dailyCommissionsAccrued);
                    BigDecimal dailyCashFlow = dailySales
                            .add(dailyAdditionalIncome)
                            .subtract(dailyExpenses)
                            .subtract(dailyAdvances)
                            .subtract(dailyPayments);

                    return DailyProfitabilityPointResponse.builder()
                            .date(p.getReportDate())
                            .totalSales(dailySales)
                            .additionalIncome(dailyAdditionalIncome)
                            .operationalExpenses(dailyExpenses)
                            .barberAdvances(dailyAdvances)
                            .barberPayments(dailyPayments)
                            .barberCommissionsAccrued(dailyCommissionsAccrued)
                            .cashFlowAfterBarberSettlements(dailyCashFlow)
                            .netProfit(dailyProfit)
                            .build();
                })
                .toList();

        return ProfitabilityReportResponse.builder()
                .totalSales(totalSales)
                .cashSales(cashSales)
                .nonCashSales(nonCashSales)
                .additionalIncome(additionalIncome)
                .operationalExpenses(operationalExpenses)
                .barberAdvances(barberAdvances)
                .barberPayments(barberPayments)
                .barberCommissionsAccrued(barberCommissionsAccrued)
                .cashFlowAfterBarberSettlements(cashFlowAfterBarberSettlements)
                .netProfit(netProfit)
                .profitMargin(profitMargin)
                .dailyProfitability(daily)
                .build();
    }

    @Override
    public OwnerSalesReportResponse getSalesReport(
            Long tenantId,
            Long branchId,
            LocalDate from,
            LocalDate to
    ) {

        validateAdvancedReportsAllowed(tenantId);
        LocalDateTime start = startOfDay(from);
        LocalDateTime end = endInclusive(to);

        BigDecimal totalSales = nvl(saleRepository.getTotalSalesByRange(tenantId, branchId, start, end));
        Long totalSalesCount = nvl(saleRepository.countSalesByRange(tenantId, branchId, start, end));
        Long paidSalesCount = nvl(saleRepository.countPaidSalesByRange(tenantId, branchId, start, end));
        Long courtesySalesCount = nvl(saleRepository.countCourtesySalesByRange(tenantId, branchId, start, end));
        BigDecimal courtesyReferenceAmount = nvl(saleRepository.sumCourtesyReferenceAmountByRange(tenantId, branchId, start, end));
        Long activeBarbers = nvl(saleRepository.countActiveBarbersByRange(tenantId, branchId, start, end));

        BigDecimal averageTicket = calculateAverage(totalSales, paidSalesCount);

        List<BarberSalesSummaryResponse> barberSummaries = saleRepository
                .getBarberSalesSummary(tenantId, branchId, start, end)
                .stream()
                .map(this::toBarberSummary)
                .toList();

        return OwnerSalesReportResponse.builder()
                .totalSales(totalSales)
                .totalSalesCount(totalSalesCount)
                .paidSalesCount(paidSalesCount)
                .courtesySalesCount(courtesySalesCount)
                .courtesyReferenceAmount(courtesyReferenceAmount)
                .averageTicket(averageTicket)
                .activeBarbers(activeBarbers)
                .barberSummaries(barberSummaries)
                .build();
    }

    @Override
    public List<BarberSaleDetailResponse> getBarberSalesDetail(
            Long tenantId,
            Long branchId,
            Long barberId,
            LocalDate from,
            LocalDate to
    ) {
        validateAdvancedReportsAllowed(tenantId);
        LocalDateTime start = startOfDay(from);
        LocalDateTime end = endInclusive(to);

        return saleRepository
                .getBarberSaleDetails(tenantId, branchId, barberId, start, end)
                .stream()
                .map(this::toBarberDetail)
                .toList();
    }

    @Override
    public BranchSummaryResponse getBranchSummary(
            Long tenantId,
            LocalDate from,
            LocalDate to
    ) {
        validateAdvancedReportsAllowed(tenantId);
        LocalDateTime start = startOfDay(from);
        LocalDateTime end = endInclusive(to);

        BigDecimal totalSales = nvl(saleRepository.getTotalSalesByRange(tenantId, null, start, end));
        Long totalSalesCount = nvl(saleRepository.countSalesByRange(tenantId, null, start, end));
        Long paidSalesCount = nvl(saleRepository.countPaidSalesByRange(tenantId, null, start, end));
        Long activeBarbers = nvl(saleRepository.countActiveBarbersByRange(tenantId, null, start, end));

        return BranchSummaryResponse.builder()
                .totalSales(totalSales)
                .totalSalesCount(totalSalesCount)
                .averageTicket(calculateAverage(totalSales, paidSalesCount))
                .activeBarbers(activeBarbers)
                .build();
    }

    @Override
    public BranchDetailResponse getBranchDetail(
            Long tenantId,
            Long branchId,
            LocalDate from,
            LocalDate to
    ) {
        validateAdvancedReportsAllowed(tenantId);
        LocalDateTime start = startOfDay(from);
        LocalDateTime end = endInclusive(to);

        BigDecimal totalSales = nvl(saleRepository.getTotalSalesByRange(tenantId, branchId, start, end));
        Long totalSalesCount = nvl(saleRepository.countSalesByRange(tenantId, branchId, start, end));
        Long paidSalesCount = nvl(saleRepository.countPaidSalesByRange(tenantId, branchId, start, end));
        Long activeBarbers = nvl(saleRepository.countActiveBarbersByRange(tenantId, branchId, start, end));

        List<BarberSalesSummaryResponse> barbers = getBranchBarbersReport(tenantId, branchId, from, to);
        List<TopServiceResponse> topServices = getTopServices(tenantId, branchId, from, to);
        List<DailySalesPointResponse> dailySales = getDailySales(tenantId, branchId, from, to);
        PaymentSummaryResponse paymentSummary = getPaymentSummary(tenantId, branchId, from, to);

        return BranchDetailResponse.builder()
                .branchId(branchId)
                .totalSales(totalSales)
                .totalSalesCount(totalSalesCount)
                .averageTicket(calculateAverage(totalSales, paidSalesCount))
                .activeBarbers(activeBarbers)
                .barbers(barbers)
                .topServices(topServices)
                .dailySales(dailySales)
                .paymentSummary(paymentSummary)
                .build();
    }

    @Override
    public List<BarberSalesSummaryResponse> getBranchBarbersReport(
            Long tenantId,
            Long branchId,
            LocalDate from,
            LocalDate to
    ) {
        validateAdvancedReportsAllowed(tenantId);
        LocalDateTime start = startOfDay(from);
        LocalDateTime end = endInclusive(to);

        return saleRepository
                .getBarberSalesSummary(tenantId, branchId, start, end)
                .stream()
                .map(this::toBarberSummary)
                .toList();
    }

    @Override
    public List<BarberSalesSummaryResponse> getBarberSummary(
            Long tenantId,
            Long branchId,
            LocalDate from,
            LocalDate to
    ) {
        validateAdvancedReportsAllowed(tenantId);
        return getBranchBarbersReport(tenantId, branchId, from, to);
    }

    @Override
    public List<BarberSaleDetailResponse> getBarberDetail(
            Long tenantId,
            Long branchId,
            Long barberId,
            LocalDate from,
            LocalDate to
    ) {
        validateAdvancedReportsAllowed(tenantId);
        return getBarberSalesDetail(tenantId, branchId, barberId, from, to);
    }

    @Override
    public List<DailySalesPointResponse> getDailySales(
            Long tenantId,
            Long branchId,
            LocalDate from,
            LocalDate to
    ) {
        validateAdvancedReportsAllowed(tenantId);
        return saleRepository.getDailySalesReport(
                        tenantId,
                        branchId,
                        startOfDay(from),
                        endInclusive(to)
                )
                .stream()
                .map(p -> DailySalesPointResponse.builder()
                        .date(p.getSaleDate())
                        .totalSales(nvl(p.getTotalSales()))
                        .salesCount(nvl(p.getSalesCount()))
                        .build())
                .toList();
    }

    @Override
    public List<TopServiceResponse> getTopServices(
            Long tenantId,
            Long branchId,
            LocalDate from,
            LocalDate to
    ) {
        validateAdvancedReportsAllowed(tenantId);
        return saleRepository.getTopServicesReport(
                        tenantId,
                        branchId,
                        startOfDay(from),
                        endInclusive(to)
                )
                .stream()
                .map(p -> TopServiceResponse.builder()
                        .serviceName(p.getServiceName())
                        .timesSold(nvl(p.getTimesSold()))
                        .totalAmount(nvl(p.getTotalAmount()))
                        .build())
                .toList();
    }

    @Override
    public Map<String, Object> getProfessionalPaymentReport(
            Long tenantId, Long branchId, Long barberUserId, String status,
            LocalDate from, LocalDate to
    ) {
        validateAdvancedReportsAllowed(tenantId);
        BarberPaymentStatus selectedStatus = status == null || status.isBlank()
                ? null : BarberPaymentStatus.valueOf(status.trim().toUpperCase());
        List<BarberPayment> payments = barberPaymentRepository.findProfessionalPaymentReport(
                tenantId, branchId, barberUserId, selectedStatus, from, to
        );
        BigDecimal totalSalary = BigDecimal.ZERO;
        BigDecimal totalCommissions = BigDecimal.ZERO;
        BigDecimal totalTips = BigDecimal.ZERO;
        BigDecimal totalAdvances = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        BigDecimal totalPending = BigDecimal.ZERO;
        List<Map<String, Object>> items = new java.util.ArrayList<>();
        for (BarberPayment payment : payments) {
            LocalDateTime periodStart = payment.getPeriodFrom().atStartOfDay();
            LocalDateTime periodEnd = payment.getPeriodTo().plusDays(1).atStartOfDay();
            BigDecimal salary = nvl(payment.getSalaryAmount());
            BigDecimal commissions = nvl(payment.getCommissionAmount());
            BigDecimal tips = nvl(saleRepository.sumBarberTipsByRange(
                    tenantId, payment.getBranch().getId(), payment.getBarberUser().getId(), periodStart, periodEnd
            ));
            BigDecimal advances = nvl(payment.getAdvancesApplied());
            BigDecimal paid = nvl(payment.getAmountPaid());
            BigDecimal pending = nvl(payment.getRemainingAmount());
            totalSalary = totalSalary.add(salary); totalCommissions = totalCommissions.add(commissions);
            totalTips = totalTips.add(tips); totalAdvances = totalAdvances.add(advances);
            totalPaid = totalPaid.add(paid); totalPending = totalPending.add(pending);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("paymentId", payment.getId()); item.put("barberUserId", payment.getBarberUser().getId());
            item.put("barberName", (nvlText(payment.getBarberUser().getNombre()) + " " + nvlText(payment.getBarberUser().getApellido())).trim()); item.put("branchId", payment.getBranch().getId());
            item.put("branchName", payment.getBranch().getNombre()); item.put("paymentMode", payment.getPaymentMode().name());
            item.put("status", payment.getStatus().name()); item.put("periodFrom", payment.getPeriodFrom().toString());
            item.put("periodTo", payment.getPeriodTo().toString()); item.put("salaryAmount", salary);
            item.put("commissionAmount", commissions); item.put("tipsAmount", tips); item.put("advancesApplied", advances);
            item.put("amountPaid", paid); item.put("remainingAmount", pending);
            item.put("paymentMethod", payment.getPaymentMethod().name()); item.put("createdAt", payment.getCreatedAt().toString());
            items.add(item);
        }
        if (branchId != null && (selectedStatus == null || selectedStatus == BarberPaymentStatus.PENDING)) {
            saleRepository.getBarberSalesSummary(tenantId, branchId, startOfDay(from), endInclusive(to)).stream()
                    .filter(barber -> barberUserId == null || barberUserId.equals(barber.getBarberId()))
                    .forEach(barber -> {
                        BarberPaymentPreviewResponse preview = barberPaymentService.preview(tenantId, branchId, barber.getBarberId(), from, to);
                        if (nvl(preview.getPendingAmount()).compareTo(BigDecimal.ZERO) <= 0) return;
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("paymentId", "pending-" + barber.getBarberId()); item.put("barberUserId", barber.getBarberId());
                        item.put("barberName", barber.getBarberName()); item.put("branchId", branchId); item.put("branchName", "Sede seleccionada");
                        item.put("paymentMode", preview.getPaymentMode()); item.put("status", "PENDING");
                        item.put("periodFrom", preview.getPeriodFrom().toString()); item.put("periodTo", preview.getPeriodTo().toString());
                        item.put("salaryAmount", nvl(preview.getSalaryAmount())); item.put("commissionAmount", nvl(preview.getCommissionAmount()));
                        item.put("tipsAmount", nvl(preview.getTipsAmount())); item.put("advancesApplied", nvl(preview.getAdvancesApplied()));
                        item.put("amountPaid", BigDecimal.ZERO); item.put("remainingAmount", nvl(preview.getPendingAmount()));
                        item.put("paymentMethod", ""); item.put("createdAt", ""); items.add(item);
                    });
            totalSalary = items.stream().map(item -> (BigDecimal) item.get("salaryAmount")).reduce(BigDecimal.ZERO, BigDecimal::add);
            totalCommissions = items.stream().map(item -> (BigDecimal) item.get("commissionAmount")).reduce(BigDecimal.ZERO, BigDecimal::add);
            totalTips = items.stream().map(item -> (BigDecimal) item.get("tipsAmount")).reduce(BigDecimal.ZERO, BigDecimal::add);
            totalAdvances = items.stream().map(item -> (BigDecimal) item.get("advancesApplied")).reduce(BigDecimal.ZERO, BigDecimal::add);
            totalPaid = items.stream().map(item -> (BigDecimal) item.get("amountPaid")).reduce(BigDecimal.ZERO, BigDecimal::add);
            totalPending = items.stream().map(item -> (BigDecimal) item.get("remainingAmount")).reduce(BigDecimal.ZERO, BigDecimal::add);
        }        Map<String, Object> response = new LinkedHashMap<>();
        response.put("from", from.toString()); response.put("to", to.toString());
        response.put("branchId", branchId == null ? 0L : branchId); response.put("barberUserId", barberUserId == null ? 0L : barberUserId);
        response.put("status", selectedStatus == null ? "ALL" : selectedStatus.name()); response.put("count", items.size());
        response.put("totalSalary", totalSalary); response.put("totalCommissions", totalCommissions);
        response.put("totalTips", totalTips); response.put("totalAdvances", totalAdvances);
        response.put("totalPaid", totalPaid); response.put("totalPending", totalPending); response.put("items", items);
        return response;
    }

    @Override
    public Map<String, Object> getProductReport(
            Long tenantId, Long branchId, LocalDate from, LocalDate to
    ) {
        validateAdvancedReportsAllowed(tenantId);
        List<Map<String, Object>> items = saleRepository.getProductSalesReport(
                tenantId, branchId, startOfDay(from), endInclusive(to)
        ).stream().map(row -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("productId", row.getProductId()); item.put("productName", row.getProductName());
            item.put("sku", row.getSku()); item.put("category", row.getCategory());
            item.put("unitsSold", nvl(row.getUnitsSold())); item.put("salesCount", nvl(row.getSalesCount()));
            item.put("revenue", nvl(row.getRevenue())); item.put("estimatedCost", nvl(row.getEstimatedCost()));
            item.put("estimatedMargin", nvl(row.getEstimatedMargin()));
            return item;
        }).toList();
        long unitsSold = items.stream().mapToLong(item -> ((Number) item.get("unitsSold")).longValue()).sum();
        BigDecimal revenue = items.stream().map(item -> (BigDecimal) item.get("revenue")).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal estimatedCost = items.stream().map(item -> (BigDecimal) item.get("estimatedCost")).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("from", from.toString()); response.put("to", to.toString()); response.put("branchId", branchId == null ? 0L : branchId);
        response.put("productCount", items.size()); response.put("unitsSold", unitsSold); response.put("revenue", revenue);
        response.put("estimatedCost", estimatedCost); response.put("estimatedMargin", revenue.subtract(estimatedCost)); response.put("items", items);
        return response;
    }

    @Override
    public Map<String, Object> getExpenseReport(
            Long tenantId, Long branchId, LocalDate from, LocalDate to, String type
    ) {
        validateAdvancedReportsAllowed(tenantId);
        CashMovementType selectedType = type == null || type.isBlank() ? null : CashMovementType.valueOf(type.trim().toUpperCase());
        List<CashMovementType> expenseTypes = List.of(CashMovementType.EXPENSE, CashMovementType.ADVANCE_BARBER, CashMovementType.PAYMENT_BARBER);
        if (selectedType != null && !expenseTypes.contains(selectedType)) throw new IllegalArgumentException("Tipo de gasto no válido");
        List<CashMovement> movements = cashMovementRepository.findExpenseReportMovements(
                tenantId, branchId, expenseTypes, selectedType, startOfDay(from), endInclusive(to)
        );
        Map<String, BigDecimal> totalsByType = new LinkedHashMap<>();
        for (CashMovementType value : expenseTypes) totalsByType.put(value.name(), BigDecimal.ZERO);
        List<Map<String, Object>> items = movements.stream().map(movement -> {
            totalsByType.compute(movement.getType().name(), (key, total) -> nvl(total).add(nvl(movement.getAmount())));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", movement.getId()); item.put("date", movement.getMovementDate().toString());
            item.put("branchId", movement.getBranch().getId()); item.put("branchName", movement.getBranch().getNombre());
            item.put("type", movement.getType().name()); item.put("amount", nvl(movement.getAmount()));
            item.put("concept", movement.getConcept()); item.put("note", movement.getNote() == null ? "" : movement.getNote());
            item.put("professional", movement.getBarberUser() == null ? "" : movement.getBarberUser().getNombre());
            item.put("paymentMethod", movement.getPaymentMethod() == null ? "" : movement.getPaymentMethod().name());
            return item;
        }).toList();
        BigDecimal total = totalsByType.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("from", from.toString()); response.put("to", to.toString());
        response.put("branchId", branchId == null ? 0L : branchId);
        response.put("type", selectedType == null ? "ALL" : selectedType.name());
        response.put("total", total); response.put("count", items.size());
        response.put("totalsByType", totalsByType); response.put("items", items);
        return response;
    }

    @Override
    public PaymentSummaryResponse getPaymentSummary(
            Long tenantId,
            Long branchId,
            LocalDate from,
            LocalDate to
    ) {
        validateAdvancedReportsAllowed(tenantId);
        LocalDateTime start = startOfDay(from);
        LocalDateTime end = endInclusive(to);

        BigDecimal cash = nvl(saleRepository.getTotalByPaymentMethod(tenantId, branchId, "EFECTIVO", start, end));
        BigDecimal yape = nvl(saleRepository.getTotalByPaymentMethod(tenantId, branchId, "YAPE", start, end));
        BigDecimal card = nvl(saleRepository.getTotalByPaymentMethod(tenantId, branchId, "TARJETA", start, end));
        BigDecimal transfer = nvl(saleRepository.getTotalByPaymentMethod(tenantId, branchId, "TRANSFER", start, end));
        BigDecimal plin = nvl(saleRepository.getTotalByPaymentMethod(tenantId, branchId, "PLIN", start, end));
        BigDecimal gratis = nvl(saleRepository.getTotalByPaymentMethod(tenantId, branchId, "GRATIS", start, end));
        Long freeCount = nvl(saleRepository.countCourtesySalesByRange(tenantId, branchId, start, end));
        BigDecimal freeReferenceAmount = nvl(saleRepository.sumCourtesyReferenceAmountByRange(tenantId, branchId, start, end));

        BigDecimal total = cash.add(yape).add(card).add(transfer).add(plin);

        return PaymentSummaryResponse.builder()
                .cash(cash)
                .yape(yape)
                .card(card)
                .free(gratis)
                .freeCount(freeCount)
                .freeReferenceAmount(freeReferenceAmount)
                .transfer(transfer)
                .plin(plin)
                .total(total)
                .build();
    }

    private BarberSalesSummaryResponse toBarberSummary(BarberSalesSummaryProjection p) {

        BigDecimal total = nvl(p.getTotalSales());
        Long count = nvl(p.getSalesCount());
        Long paidSalesCount = nvl(p.getPaidSalesCount());
        Long courtesySalesCount = nvl(p.getCourtesySalesCount());
        BigDecimal courtesyReferenceAmount = nvl(p.getCourtesyReferenceAmount());

        return BarberSalesSummaryResponse.builder()
                .barberId(p.getBarberId())
                .barberName(p.getBarberName())
                .totalSales(total)
                .salesCount(count)
                .paidSalesCount(paidSalesCount)
                .courtesySalesCount(courtesySalesCount)
                .courtesyReferenceAmount(courtesyReferenceAmount)
                .averageTicket(calculateAverage(total, paidSalesCount))
                .build();
    }

    private BarberSaleDetailResponse toBarberDetail(BarberSaleDetailProjection p) {

        return BarberSaleDetailResponse.builder()
                .saleId(p.getSaleId())
                .customerName(p.getCustomerName())
                .serviceNames(p.getServiceNames())
                .total(nvl(p.getTotal()))
                .subtotal(nvl(p.getSubtotal()))
                .discount(nvl(p.getDiscount()))
                .serviceCommissionAmountApplied(nvl(p.getServiceCommissionAmountApplied()))
                .productCommissionAmountApplied(nvl(p.getProductCommissionAmountApplied()))
                .commissionAmountApplied(nvl(p.getCommissionAmountApplied()))
                .effectiveCommissionPercentage(nvl(p.getEffectiveCommissionPercentage()))
                .ownerNetAmount(nvl(p.getTotal()).subtract(nvl(p.getCommissionAmountApplied())).max(BigDecimal.ZERO))
                .commissionSnapshotComplete(Boolean.TRUE.equals(p.getCommissionSnapshotComplete()))
                .paymentMethod(p.getPaymentMethod())
                .createdAt(p.getCreatedAt() != null ? p.getCreatedAt().toString() : "")
                .build();
    }

    private BigDecimal calculateAverage(BigDecimal total, Long count) {
        if (count == null || count <= 0) return BigDecimal.ZERO;
        return total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private String nvlText(String value) {
        return value == null ? "" : value;
    }
    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private Long nvl(Long value) {
        return value == null ? 0L : value;
    }

    private LocalDateTime startOfDay(LocalDate date) {
        return date.atStartOfDay();
    }

    private LocalDateTime endInclusive(LocalDate date) {
        return date.plusDays(1).atStartOfDay().minusNanos(1);
    }
    private void validateAdvancedReportsAllowed(Long tenantId) {
        Subscription subscription = subscriptionRepository
                .findTopByTenantIdOrderByFechaInicioDesc(tenantId)
                .orElseThrow(() -> new RuntimeException("Suscripción no encontrada"));

        String estado = subscription.getEstado() == null
                ? ""
                : subscription.getEstado().trim().toUpperCase();

        if (!"ACTIVE".equals(estado) && !"TRIAL".equals(estado) && !"PENDING_REVIEW".equals(estado)) {
            throw new RuntimeException("Tu suscripción no permite acceder a reportes");
        }
    }

}

package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.response.*;

import com.gods.saas.domain.model.Subscription;
import com.gods.saas.domain.repository.SaleRepository;
import com.gods.saas.domain.repository.SubscriptionRepository;
import com.gods.saas.domain.repository.projection.BarberSaleDetailProjection;
import com.gods.saas.domain.repository.projection.BarberSalesSummaryProjection;
import com.gods.saas.service.impl.impl.OwnerReportsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OwnerReportsServiceImpl implements OwnerReportsService {

    private final SaleRepository saleRepository;
    private final SubscriptionRepository subscriptionRepository;

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
        Long activeBarbers = nvl(saleRepository.countActiveBarbersByRange(tenantId, branchId, start, end));

        BigDecimal averageTicket = calculateAverage(totalSales, totalSalesCount);

        List<BarberSalesSummaryResponse> barberSummaries = saleRepository
                .getBarberSalesSummary(tenantId, branchId, start, end)
                .stream()
                .map(this::toBarberSummary)
                .toList();

        return OwnerSalesReportResponse.builder()
                .totalSales(totalSales)
                .totalSalesCount(totalSalesCount)
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
        Long activeBarbers = nvl(saleRepository.countActiveBarbersByRange(tenantId, null, start, end));

        return BranchSummaryResponse.builder()
                .totalSales(totalSales)
                .totalSalesCount(totalSalesCount)
                .averageTicket(calculateAverage(totalSales, totalSalesCount))
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
        Long activeBarbers = nvl(saleRepository.countActiveBarbersByRange(tenantId, branchId, start, end));

        List<BarberSalesSummaryResponse> barbers = getBranchBarbersReport(tenantId, branchId, from, to);
        List<TopServiceResponse> topServices = getTopServices(tenantId, branchId, from, to);
        List<DailySalesPointResponse> dailySales = getDailySales(tenantId, branchId, from, to);
        PaymentSummaryResponse paymentSummary = getPaymentSummary(tenantId, branchId, from, to);

        return BranchDetailResponse.builder()
                .branchId(branchId)
                .totalSales(totalSales)
                .totalSalesCount(totalSalesCount)
                .averageTicket(calculateAverage(totalSales, totalSalesCount))
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

        BigDecimal total = cash.add(yape).add(card).add(transfer).add(plin);

        return PaymentSummaryResponse.builder()
                .cash(cash)
                .yape(yape)
                .card(card)
                .free(gratis)
                .transfer(transfer)
                .plin(plin)
                .total(total)
                .build();
    }

    private BarberSalesSummaryResponse toBarberSummary(BarberSalesSummaryProjection p) {

        BigDecimal total = nvl(p.getTotalSales());
        Long count = nvl(p.getSalesCount());

        return BarberSalesSummaryResponse.builder()
                .barberId(p.getBarberId())
                .barberName(p.getBarberName())
                .totalSales(total)
                .salesCount(count)
                .averageTicket(calculateAverage(total, count))
                .build();
    }

    private BarberSaleDetailResponse toBarberDetail(BarberSaleDetailProjection p) {

        return BarberSaleDetailResponse.builder()
                .saleId(p.getSaleId())
                .customerName(p.getCustomerName())
                .serviceNames(p.getServiceNames())
                .total(nvl(p.getTotal()))
                .paymentMethod(p.getPaymentMethod())
                .createdAt(p.getCreatedAt() != null ? p.getCreatedAt().toString() : "")
                .build();
    }

    private BigDecimal calculateAverage(BigDecimal total, Long count) {
        if (count == null || count <= 0) return BigDecimal.ZERO;
        return total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
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

        String plan = subscription.getPlan() == null ? "" : subscription.getPlan().trim().toUpperCase();

        if ("STARTER".equals(plan)) {
            throw new RuntimeException("Tu plan actual no permite acceder a reportes avanzados");
        }
    }
}
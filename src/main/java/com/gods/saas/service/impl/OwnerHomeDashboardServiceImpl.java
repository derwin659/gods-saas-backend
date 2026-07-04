package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.response.BranchDashboardItemResponse;
import com.gods.saas.domain.dto.response.DashboardAlertResponse;
import com.gods.saas.domain.dto.response.DashboardLeaderResponse;
import com.gods.saas.domain.dto.response.OwnerHomeDashboardResponse;
import com.gods.saas.domain.dto.response.UpcomingAppointmentItemResponse;
import com.gods.saas.domain.model.Appointment;
import com.gods.saas.domain.model.Branch;
import com.gods.saas.domain.repository.AppointmentRepository;
import com.gods.saas.domain.repository.BarberAvailabilityRepository;
import com.gods.saas.domain.repository.BarberPaymentRepository;
import com.gods.saas.domain.repository.BranchRepository;
import com.gods.saas.domain.repository.CashMovementRepository;
import com.gods.saas.domain.repository.CashRegisterRepository;
import com.gods.saas.domain.repository.CustomerRepository;
import com.gods.saas.domain.repository.ProductBranchStockRepository;
import com.gods.saas.domain.repository.SaleRepository;
import com.gods.saas.domain.repository.UserTenantRoleRepository;
import com.gods.saas.domain.repository.projection.DashboardLeaderProjection;
import com.gods.saas.domain.repository.projection.TopServiceReportProjection;
import com.gods.saas.service.impl.impl.OwnerHomeDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OwnerHomeDashboardServiceImpl implements OwnerHomeDashboardService {

    private final SaleRepository saleRepository;
    private final AppointmentRepository appointmentRepository;
    private final BarberAvailabilityRepository barberAvailabilityRepository;
    private final CustomerRepository customerRepository;
    private final UserTenantRoleRepository userTenantRolesRepository;
    private final BranchRepository branchRepository;
    private final CashMovementRepository cashMovementRepository;
    private final CashRegisterRepository cashRegisterRepository;
    private final ProductBranchStockRepository productBranchStockRepository;
    private final BarberPaymentRepository barberPaymentRepository;
    private final TenantTimeService tenantTimeService;

    @Override
    public OwnerHomeDashboardResponse getDashboard(Long tenantId, Long branchId) {
        LocalDate today = tenantTimeService.today(tenantId);
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();
        LocalTime now = tenantTimeService.currentTime(tenantId);

        BigDecimal todaySales = nvl(saleRepository.sumSalesByDay(tenantId, branchId, start, end));
        BigDecimal cashSales = nvl(saleRepository.getCashSalesByRange(tenantId, branchId, start, end));
        BigDecimal cashMovements = nvl(cashMovementRepository.sumNetCashMovementsByRange(tenantId, branchId, start, end));
        BigDecimal expectedCash = cashSales.add(cashMovements);
        BigDecimal todayExpenses = nvl(cashMovementRepository.sumGeneralExpensesByRange(tenantId, branchId, start, end));
        BigDecimal todayProfessionalPayments = nvl(cashMovementRepository.sumBarberPaymentsByRange(tenantId, branchId, start, end));
        BigDecimal pendingProfessionalPayments = nvl(barberPaymentRepository.sumPendingAmount(tenantId, branchId));

        LocalDateTime yesterdayStart = today.minusDays(1).atStartOfDay();
        BigDecimal yesterdaySales = nvl(saleRepository.sumSalesByDay(tenantId, branchId, yesterdayStart, start));
        LocalDateTime previousWeekStart = today.minusWeeks(1).atStartOfDay();
        BigDecimal previousWeekSales = nvl(saleRepository.sumSalesByDay(
                tenantId, branchId, previousWeekStart, previousWeekStart.plusDays(1)));

        Integer todayAppointments = Math.toIntExact(
                appointmentRepository.countTodayAppointments(tenantId, branchId, today)
        );
        Integer activeBarbers = userTenantRolesRepository.countActiveBarbers(tenantId, branchId);
        Integer newClients = customerRepository.countCustomers(tenantId);
        BigDecimal averageTicket = nvl(saleRepository.averageTicketByDay(tenantId, branchId, start, end));

        List<UpcomingAppointmentItemResponse> upcomingAppointments =
                appointmentRepository.findUpcomingAppointmentsForDashboard(
                        tenantId, branchId, today, now
                ).stream().map(this::mapUpcoming).toList();

        List<BranchDashboardItemResponse> branches = branchRepository
                .findByTenantIdAndActivoTrueOrderByNombreAsc(tenantId)
                .stream()
                .filter(branch -> branchId == null || branch.getId().equals(branchId))
                .map(branch -> mapBranchDashboard(branch, tenantId, today, start, end))
                .toList();

        DashboardLeaderResponse topBarber = saleRepository
                .getTopBarberForDashboard(tenantId, branchId, start, end)
                .stream()
                .findFirst()
                .map(this::mapTopBarber)
                .orElse(null);

        DashboardLeaderResponse topService = saleRepository
                .getTopServicesReport(tenantId, branchId, start, end)
                .stream()
                .findFirst()
                .map(this::mapTopService)
                .orElse(null);

        long cashDifferences = cashRegisterRepository.countClosedWithDifference(
                tenantId, branchId, start, end);
        long lowStockProducts = productBranchStockRepository.countLowStock(tenantId, branchId);
        long cancelledAppointments = appointmentRepository.countCancelledForDashboard(
                tenantId, branchId, today);
        long barbersWithoutSchedule = barberAvailabilityRepository
                .countActiveBarbersWithoutSchedule(tenantId, branchId);

        List<DashboardAlertResponse> alerts = buildAlerts(
                expectedCash, pendingProfessionalPayments, todaySales,
                yesterdaySales, previousWeekSales, cashDifferences,
                lowStockProducts, cancelledAppointments, barbersWithoutSchedule, branches
        );
        return OwnerHomeDashboardResponse.builder()
                .todaySales(todaySales)
                .expectedCash(nvl(expectedCash))
                .todayAppointments(nvl(todayAppointments))
                .activeBarbers(nvl(activeBarbers))
                .newClients(nvl(newClients))
                .averageTicket(averageTicket)
                .todayExpenses(todayExpenses)
                .todayProfessionalPayments(todayProfessionalPayments)
                .pendingProfessionalPayments(pendingProfessionalPayments)
                .yesterdaySales(yesterdaySales)
                .previousWeekSales(previousWeekSales)
                .topBarber(topBarber)
                .topService(topService)
                .alerts(alerts)
                .upcomingAppointments(upcomingAppointments)
                .branches(branches)
                .build();
    }

    private BranchDashboardItemResponse mapBranchDashboard(
            Branch branch,
            Long tenantId,
            LocalDate today,
            LocalDateTime start,
            LocalDateTime end
    ) {
        Long currentBranchId = branch.getId();
        BigDecimal branchTodaySales = saleRepository.sumSalesByDay(
                tenantId, currentBranchId, start, end
        );
        Integer branchTodayAppointments = Math.toIntExact(
                appointmentRepository.countTodayAppointments(tenantId, currentBranchId, today)
        );
        Integer branchActiveBarbers = userTenantRolesRepository.countActiveBarbers(
                tenantId, currentBranchId
        );
        BigDecimal branchAverageTicket = saleRepository.averageTicketByDay(
                tenantId, currentBranchId, start, end
        );

        return BranchDashboardItemResponse.builder()
                .branchId(branch.getId())
                .branchName(branch.getNombre())
                .todaySales(nvl(branchTodaySales))
                .todayAppointments(nvl(branchTodayAppointments))
                .activeBarbers(nvl(branchActiveBarbers))
                .averageTicket(nvl(branchAverageTicket))
                .build();
    }

    private DashboardLeaderResponse mapTopBarber(DashboardLeaderProjection item) {
        return DashboardLeaderResponse.builder()
                .name(item.getName())
                .amount(nvl(item.getAmount()))
                .count(item.getCount() == null ? 0L : item.getCount())
                .build();
    }

    private DashboardLeaderResponse mapTopService(TopServiceReportProjection item) {
        return DashboardLeaderResponse.builder()
                .name(item.getServiceName())
                .amount(nvl(item.getTotalAmount()))
                .count(item.getTimesSold() == null ? 0L : item.getTimesSold())
                .build();
    }

    private List<DashboardAlertResponse> buildAlerts(
            BigDecimal expectedCash, BigDecimal pendingProfessionalPayments,
            BigDecimal todaySales, BigDecimal yesterdaySales, BigDecimal previousWeekSales,
            long cashDifferences, long lowStockProducts, long cancelledAppointments,
            long barbersWithoutSchedule, List<BranchDashboardItemResponse> branches
    ) {
        List<DashboardAlertResponse> alerts = new ArrayList<>();
        if (cashDifferences > 0) {
            alerts.add(DashboardAlertResponse.builder().type("CASH_DIFFERENCE")
                    .title("Caja con diferencia")
                    .message(cashDifferences + " cierre(s) de hoy requieren revisión.")
                    .severity("CRITICAL").build());
        }
        if (expectedCash.compareTo(BigDecimal.ZERO) < 0) {
            alerts.add(DashboardAlertResponse.builder().type("NEGATIVE_EXPECTED_CASH")
                    .title("Caja esperada negativa")
                    .message("Los egresos en efectivo superan los ingresos registrados hoy.")
                    .severity("CRITICAL").build());
        }
        if (lowStockProducts > 0) {
            alerts.add(DashboardAlertResponse.builder().type("LOW_STOCK").title("Stock bajo")
                    .message(lowStockProducts + " producto(s) llegaron a su nivel mínimo.")
                    .severity("WARNING").build());
        }
        if (pendingProfessionalPayments.compareTo(BigDecimal.ZERO) > 0) {
            alerts.add(DashboardAlertResponse.builder().type("PROFESSIONAL_PAYMENTS_PENDING")
                    .title("Pagos profesionales pendientes")
                    .message("Hay " + nvl(pendingProfessionalPayments) + " por completar.")
                    .severity("WARNING").build());
        }
        if (cancelledAppointments > 0) {
            alerts.add(DashboardAlertResponse.builder().type("CANCELLED_APPOINTMENTS")
                    .title("Citas canceladas hoy")
                    .message(cancelledAppointments + " cita(s) fueron canceladas.")
                    .severity("WARNING").build());
        }
        if (barbersWithoutSchedule > 0) {
            alerts.add(DashboardAlertResponse.builder().type("BARBER_WITHOUT_SCHEDULE")
                    .title("Profesional sin horario")
                    .message(barbersWithoutSchedule + " profesional(es) activos no tienen horario configurado.")
                    .severity("WARNING").build());
        }
        BigDecimal referenceSales = yesterdaySales.max(previousWeekSales);
        if (todaySales.compareTo(BigDecimal.ZERO) > 0
                && referenceSales.compareTo(BigDecimal.ZERO) > 0
                && todaySales.compareTo(referenceSales.multiply(new BigDecimal("0.50"))) < 0) {
            alerts.add(DashboardAlertResponse.builder().type("LOW_SALES")
                    .title("Ventas por debajo de la referencia")
                    .message("Las ventas de hoy están más de 50% por debajo del mejor periodo comparable.")
                    .severity("INFO").build());
        }
        long branchesWithoutSales = branches.stream()
                .filter(item -> nvl(item.getTodaySales()).compareTo(BigDecimal.ZERO) == 0).count();
        if (branches.size() > 1 && branchesWithoutSales > 0) {
            alerts.add(DashboardAlertResponse.builder().type("BRANCH_WITHOUT_SALES")
                    .title("Sede sin ventas hoy")
                    .message(branchesWithoutSales + " sede(s) todavía no registran ventas.")
                    .severity("INFO").build());
        }
        return alerts;
    }
    private UpcomingAppointmentItemResponse mapUpcoming(Appointment appointment) {
        return UpcomingAppointmentItemResponse.builder()
                .appointmentId(appointment.getId())
                .time(appointment.getHoraInicio() != null ? appointment.getHoraInicio().toString() : "")
                .customerName(appointment.getCustomer() != null ? appointment.getCustomer().getNombres() : "Cliente")
                .serviceName(appointment.getService() != null ? appointment.getService().getNombre() : "Servicio")
                .barberName(appointment.getUser() != null ? appointment.getUser().getNombre() : "")
                .build();
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : value.setScale(2, RoundingMode.HALF_UP);
    }

    private Integer nvl(Integer value) {
        return value == null ? 0 : value;
    }
}
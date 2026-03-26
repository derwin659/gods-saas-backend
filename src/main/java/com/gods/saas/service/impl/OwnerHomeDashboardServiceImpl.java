package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.response.BranchDashboardItemResponse;
import com.gods.saas.domain.dto.response.OwnerHomeDashboardResponse;
import com.gods.saas.domain.dto.response.UpcomingAppointmentItemResponse;
import com.gods.saas.domain.model.Appointment;
import com.gods.saas.domain.model.Branch;
import com.gods.saas.domain.repository.AppointmentRepository;
import com.gods.saas.domain.repository.BranchRepository;
import com.gods.saas.domain.repository.CustomerRepository;
import com.gods.saas.domain.repository.SaleRepository;
import com.gods.saas.domain.repository.UserTenantRoleRepository;
import com.gods.saas.service.impl.impl.OwnerHomeDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OwnerHomeDashboardServiceImpl implements OwnerHomeDashboardService {

    private final SaleRepository saleRepository;
    private final AppointmentRepository appointmentRepository;
    private final CustomerRepository customerRepository;
    private final UserTenantRoleRepository userTenantRolesRepository;
    private final BranchRepository branchRepository;

    private static final ZoneId ZONE = ZoneId.of("America/Lima");

    @Override
    public OwnerHomeDashboardResponse getDashboard(Long tenantId, Long branchId) {
        LocalDate today = LocalDate.now(ZONE);
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.atTime(LocalTime.MAX);

        // RESUMEN GENERAL DEL DUEÑO: TODAS LAS SEDES
        Long globalBranchId = null;

        BigDecimal todaySales = saleRepository.sumSalesByDay(tenantId, globalBranchId, start, end);
        Integer todayAppointments = Math.toIntExact(
                appointmentRepository.countTodayAppointments(tenantId, globalBranchId, today)
        );
        Integer activeBarbers = userTenantRolesRepository.countActiveBarbers(tenantId, globalBranchId);
        Integer newClients = customerRepository.countCustomers(tenantId);
        BigDecimal averageTicket = saleRepository.averageTicketByDay(tenantId, globalBranchId, start, end);
        BigDecimal todayExpenses = BigDecimal.ZERO;

        List<UpcomingAppointmentItemResponse> upcomingAppointments =
                appointmentRepository.findUpcomingAppointmentsForDashboard(
                        tenantId,
                        globalBranchId,
                        today,
                        LocalTime.now(ZONE)
                ).stream().map(this::mapUpcoming).toList();

        // DESGLOSE POR SEDE
        List<BranchDashboardItemResponse> branches = branchRepository
                .findByTenantIdAndActivoTrueOrderByNombreAsc(tenantId)
                .stream()
                .map(branch -> mapBranchDashboard(branch, tenantId, today, start, end))
                .toList();

        return OwnerHomeDashboardResponse.builder()
                .todaySales(nvl(todaySales))
                .todayAppointments(nvl(todayAppointments))
                .activeBarbers(nvl(activeBarbers))
                .newClients(nvl(newClients))
                .averageTicket(nvl(averageTicket))
                .todayExpenses(nvl(todayExpenses))
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

    private UpcomingAppointmentItemResponse mapUpcoming(Appointment a) {
        return UpcomingAppointmentItemResponse.builder()
                .appointmentId(a.getId())
                .time(a.getHoraInicio() != null ? a.getHoraInicio().toString() : "")
                .customerName(a.getCustomer() != null ? a.getCustomer().getNombres() : "Cliente")
                .serviceName(a.getService() != null ? a.getService().getNombre() : "Servicio")
                .barberName(a.getUser() != null ? a.getUser().getNombre() : "")
                .build();
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private Integer nvl(Integer value) {
        return value == null ? 0 : value;
    }
}
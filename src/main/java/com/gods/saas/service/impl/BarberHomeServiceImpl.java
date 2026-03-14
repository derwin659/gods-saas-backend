package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.response.*;
import com.gods.saas.domain.model.AppUser;
import com.gods.saas.domain.model.Appointment;
import com.gods.saas.domain.repository.AppUserRepository;
import com.gods.saas.domain.repository.AppointmentRepository;
import com.gods.saas.domain.repository.SaleRepository;
import com.gods.saas.service.impl.impl.BarberHomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BarberHomeServiceImpl implements BarberHomeService {

    private final AppUserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final SaleRepository saleRepository;

    @Override
    public BarberHomeResponse getBarberHome(Authentication authentication) {
        String userIdStr = authentication.getName();

        AppUser barber = userRepository.findById(Long.parseLong(userIdStr))
                .orElseThrow(() -> new RuntimeException("Barbero no encontrado"));

        Long tenantId = barber.getTenant().getId();
        Long barberId = barber.getId();

        LocalDate today = LocalDate.now();
        LocalDateTime startDay = today.atStartOfDay();
        LocalDateTime endDay = today.atTime(23, 59, 59);

        long citasHoy = appointmentRepository.countTodayAppointments(tenantId, barberId, today);
        long atendidosHoy = appointmentRepository.countTodayAttended(tenantId, barberId, today);
        long cancelaciones = appointmentRepository.countTodayCancelled(tenantId, barberId, today);

        BigDecimal ventasHoy = saleRepository.sumTodaySales(tenantId, barberId, startDay, endDay);
        long serviciosHoy = saleRepository.countTodayServices(tenantId, barberId, startDay, endDay);

        List<Appointment> upcoming = appointmentRepository.findUpcomingTodayAppointments(
                tenantId,
                barberId,
                today,
                java.time.LocalTime.now()
        );

        DateTimeFormatter hourFormatter = DateTimeFormatter.ofPattern("HH:mm");

        List<BarberHomeAppointmentResponse> proximasCitas = upcoming.stream()
                .limit(5)
                .map(a -> new BarberHomeAppointmentResponse(
                        a.getId(),
                        a.getHoraInicio() != null ? a.getHoraInicio().format(hourFormatter) : "",
                        buildCustomerName(a),
                        a.getService() != null ? a.getService().getNombre() : "Servicio",
                        a.getEstado() != null ? a.getEstado() : "RESERVADO"
                ))
                .toList();

        BarberQuickStatsResponse stats = new BarberQuickStatsResponse(
                (int) serviciosHoy,
                BigDecimal.ZERO, // luego conectamos propinas reales
                0,               // luego conectamos IA real
                (int) cancelaciones);

        CommissionSummaryResponse commissions = buildCommissionSummary(barber, ventasHoy);

        return BarberHomeResponse.builder()
                .tenantName(barber.getTenant() != null ? barber.getTenant().getNombre() : "Barbería")
                .barberName(barber.getNombre() != null ? barber.getNombre() : "Barbero")
                .citasHoy((int) citasHoy)
                .atendidosHoy((int) atendidosHoy)
                .ventasHoy(ventasHoy)
                .proximasCitas(proximasCitas == null ? Collections.emptyList() : proximasCitas)
                .stats(stats)
                .commissions(commissions)
                .build();


    }


    private CommissionSummaryResponse buildCommissionSummary(AppUser user, BigDecimal ventasHoy) {
        boolean salaryMode = Boolean.TRUE.equals(user.getSalaryMode());

        String scheme = user.getCommissionScheme() != null
                ? user.getCommissionScheme().trim().toUpperCase()
                : "NONE";

        BigDecimal percentage = user.getCommissionPercentage() != null
                ? user.getCommissionPercentage()
                : BigDecimal.ZERO;

        BigDecimal commissionToday = BigDecimal.ZERO;
        BigDecimal baseAmountToday = ventasHoy != null ? ventasHoy : BigDecimal.ZERO;

        if (salaryMode) {
            scheme = "FIXED";
            commissionToday = BigDecimal.ZERO;
        } else if ("PERCENTAGE".equals(scheme) && percentage.compareTo(BigDecimal.ZERO) > 0) {
            commissionToday = baseAmountToday
                    .multiply(percentage)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else {
            scheme = "NONE";
        }

        return CommissionSummaryResponse.builder()
                .scheme(scheme)
                .percentage(percentage.setScale(2, RoundingMode.HALF_UP))
                .baseAmountToday(baseAmountToday.setScale(2, RoundingMode.HALF_UP))
                .commissionToday(commissionToday.setScale(2, RoundingMode.HALF_UP))
                .salaryMode(salaryMode)
                .build();
    }

    private String buildCustomerName(Appointment a) {
        if (a.getCustomer() == null) return "Cliente";

        String nombre = a.getCustomer().getNombres() != null ? a.getCustomer().getNombres().trim() : "";
        String apellido = a.getCustomer().getApellidos() != null ? a.getCustomer().getApellidos().trim() : "";

        String full = (nombre + " " + apellido).trim();
        return full.isEmpty() ? "Cliente" : full;
    }
}

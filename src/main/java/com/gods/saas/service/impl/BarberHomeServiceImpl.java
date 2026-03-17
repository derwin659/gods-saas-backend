package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.response.BarberHomeAppointmentResponse;
import com.gods.saas.domain.dto.response.BarberHomeResponse;
import com.gods.saas.domain.dto.response.BarberQuickStatsResponse;
import com.gods.saas.domain.dto.response.CommissionSummaryResponse;
import com.gods.saas.domain.model.AppUser;
import com.gods.saas.domain.model.Appointment;
import com.gods.saas.domain.model.TenantSettings;
import com.gods.saas.domain.repository.AppUserRepository;
import com.gods.saas.domain.repository.AppointmentRepository;
import com.gods.saas.domain.repository.SaleRepository;
import com.gods.saas.domain.repository.TenantSettingsRepository;
import com.gods.saas.service.impl.impl.BarberHomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BarberHomeServiceImpl implements BarberHomeService {

    private static final String DEFAULT_TIMEZONE = "America/Lima";

    private final AppUserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final SaleRepository saleRepository;
    private final TenantSettingsRepository tenantSettingsRepository;

    @Override
    public BarberHomeResponse getBarberHome(Authentication authentication) {
        String userIdStr = authentication.getName();

        AppUser barber = userRepository.findById(Long.parseLong(userIdStr))
                .orElseThrow(() -> new RuntimeException("Barbero no encontrado"));

        Long tenantId = barber.getTenant().getId();
        Long barberId = barber.getId();

        ZoneId tenantZone = resolveTenantZoneId(tenantId);

        // Fecha y hora actuales según la zona horaria configurada del tenant
        LocalDate today = LocalDate.now(tenantZone);
        LocalTime now = LocalTime.now(tenantZone);

        // Rango del día del tenant convertido a UTC para consultar timestamps en BD
        LocalDateTime startDayUtc = today.atStartOfDay(tenantZone)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();

        LocalDateTime endDayUtc = today.plusDays(1).atStartOfDay(tenantZone)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();

        long citasHoy = appointmentRepository.countTodayAppointments(tenantId, barberId, today);
        long atendidosHoy = appointmentRepository.countTodayAttended(tenantId, barberId, today);
        long cancelaciones = appointmentRepository.countTodayCancelled(tenantId, barberId, today);

        BigDecimal ventasHoy = nvl(saleRepository.sumTodaySales(tenantId, barberId, startDayUtc, endDayUtc));
        long serviciosHoy = saleRepository.countTodayServices(tenantId, barberId, startDayUtc, endDayUtc);

        List<Appointment> upcoming = appointmentRepository.findUpcomingTodayAppointments(
                tenantId,
                barberId,
                today,
                now
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
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), // luego conectamos propinas reales
                0, // luego conectamos IA real
                (int) cancelaciones
        );

        CommissionSummaryResponse commissions = buildCommissionSummary(barber, ventasHoy);

        return BarberHomeResponse.builder()
                .tenantName(barber.getTenant() != null ? barber.getTenant().getNombre() : "Barbería")
                .barberName(buildBarberName(barber))
                .citasHoy((int) citasHoy)
                .atendidosHoy((int) atendidosHoy)
                .ventasHoy(ventasHoy.setScale(2, RoundingMode.HALF_UP))
                .proximasCitas(proximasCitas == null ? Collections.emptyList() : proximasCitas)
                .stats(stats)
                .commissions(commissions)
                .build();
    }

    private ZoneId resolveTenantZoneId(Long tenantId) {
        String timezone = tenantSettingsRepository.findByTenantId(tenantId)
                .map(TenantSettings::getTimezone)
                .filter(tz -> tz != null && !tz.trim().isEmpty())
                .orElse(DEFAULT_TIMEZONE);

        try {
            return ZoneId.of(timezone.trim());
        } catch (Exception e) {
            return ZoneId.of(DEFAULT_TIMEZONE);
        }
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

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String buildCustomerName(Appointment a) {
        if (a.getCustomer() == null) return "Cliente";

        String nombre = a.getCustomer().getNombres() != null ? a.getCustomer().getNombres().trim() : "";
        String apellido = a.getCustomer().getApellidos() != null ? a.getCustomer().getApellidos().trim() : "";

        String full = (nombre + " " + apellido).trim();
        return full.isEmpty() ? "Cliente" : full;
    }

    private String buildBarberName(AppUser barber) {
        if (barber == null) return "Barbero";

        String nombre = barber.getNombre() != null ? barber.getNombre().trim() : "";
        String apellido = barber.getApellido() != null ? barber.getApellido().trim() : "";

        String full = (nombre + " " + apellido).trim();
        return full.isEmpty() ? "Barbero" : full;
    }
}
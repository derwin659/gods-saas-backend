package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.response.BarberHomeAppointmentResponse;
import com.gods.saas.domain.dto.response.BarberHomeResponse;
import com.gods.saas.domain.dto.response.BarberQuickStatsResponse;
import com.gods.saas.domain.dto.response.CommissionSummaryResponse;
import com.gods.saas.domain.model.AppUser;
import com.gods.saas.domain.model.Appointment;
import com.gods.saas.domain.model.RoleType;
import com.gods.saas.domain.model.TenantSettings;
import com.gods.saas.domain.model.UserTenantRole;
import com.gods.saas.domain.repository.AppUserRepository;
import com.gods.saas.domain.repository.AppointmentRepository;
import com.gods.saas.domain.repository.SaleRepository;
import com.gods.saas.domain.repository.TenantSettingsRepository;
import com.gods.saas.domain.repository.UserTenantRoleRepository;
import com.gods.saas.exception.BusinessException;
import com.gods.saas.service.impl.impl.BarberHomeService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BarberHomeServiceImpl implements BarberHomeService {

    private static final String DEFAULT_TIMEZONE = "America/Lima";

    private final AppUserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final SaleRepository saleRepository;
    private final TenantSettingsRepository tenantSettingsRepository;
    private final UserTenantRoleRepository userTenantRoleRepository;
    private final CloudinaryStorageService cloudinaryStorageService;

    @Override
    public BarberHomeResponse getBarberHome(Authentication authentication) {
        AppUser barber = getCurrentBarber();
        Long tenantId = getCurrentTenantId();
        Long branchId = getCurrentBranchId();
        Long barberId = barber.getId();

        ZoneId tenantZone = resolveTenantZoneId(tenantId);

        LocalDate today = LocalDate.now(tenantZone);
        LocalTime now = LocalTime.now(tenantZone);

        LocalDateTime startDayUtc = today.atStartOfDay(tenantZone)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();

        LocalDateTime endDayUtc = today.plusDays(1).atStartOfDay(tenantZone)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();

        long citasHoy = appointmentRepository.countTodayAppointments(tenantId, barberId, today);
        long atendidosHoy = appointmentRepository.countTodayAttended(tenantId, barberId, today);
        long cancelaciones = appointmentRepository.countTodayCancelled(tenantId, barberId, today);

        BigDecimal ventasHoy = nvl(
                saleRepository.sumTodaySales(
                        tenantId,
                        branchId,
                        barberId,
                        startDayUtc,
                        endDayUtc
                )
        );

        long serviciosHoy = saleRepository.countTodayServices(
                tenantId,
                branchId,
                barberId,
                startDayUtc,
                endDayUtc
        );

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
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                0,
                (int) cancelaciones
        );

        CommissionSummaryResponse commissions = buildCommissionSummary(barber, ventasHoy);

        return BarberHomeResponse.builder()
                .tenantName(barber.getTenant() != null ? barber.getTenant().getNombre() : "Barbería")
                .barberName(buildBarberName(barber))
                .barberPhotoUrl(barber.getPhotoUrl())
                .citasHoy((int) citasHoy)
                .atendidosHoy((int) atendidosHoy)
                .ventasHoy(ventasHoy.setScale(2, RoundingMode.HALF_UP))
                .proximasCitas(proximasCitas == null ? Collections.emptyList() : proximasCitas)
                .stats(stats)
                .commissions(commissions)
                .build();
    }

    @Override
    @Transactional
    public BarberHomeResponse uploadMyPhoto(Authentication authentication, MultipartFile file) {
        AppUser barber = getCurrentBarber();
        Long tenantId = getCurrentTenantId();

        validateCurrentUserIsBarber(barber.getId(), tenantId);

        String oldPublicId = barber.getPhotoPublicId();

        CloudinaryStorageService.UploadResult result =
                cloudinaryStorageService.uploadBarberPhoto(tenantId, barber.getId(), file);

        barber.setPhotoUrl(result.getSecureUrl());
        barber.setPhotoPublicId(result.getPublicId());
        barber.setFechaActualizacion(LocalDateTime.now());

        userRepository.save(barber);

        if (oldPublicId != null && !oldPublicId.isBlank()) {
            cloudinaryStorageService.deleteImage(oldPublicId);
        }

        return getBarberHome(authentication);
    }

    @Override
    @Transactional
    public BarberHomeResponse deleteMyPhoto(Authentication authentication) {
        AppUser barber = getCurrentBarber();
        Long tenantId = getCurrentTenantId();

        validateCurrentUserIsBarber(barber.getId(), tenantId);

        String oldPublicId = barber.getPhotoPublicId();

        barber.setPhotoUrl(null);
        barber.setPhotoPublicId(null);
        barber.setFechaActualizacion(LocalDateTime.now());

        userRepository.save(barber);

        if (oldPublicId != null && !oldPublicId.isBlank()) {
            cloudinaryStorageService.deleteImage(oldPublicId);
        }

        return getBarberHome(authentication);
    }

    private void validateCurrentUserIsBarber(Long userId, Long tenantId) {
        UserTenantRole role = userTenantRoleRepository
                .findByUser_IdAndTenant_Id(userId, tenantId)
                .orElseThrow(() -> new BusinessException("El usuario no tiene rol asignado en este tenant"));

        if (role.getRole() != RoleType.BARBER) {
            throw new BusinessException("El usuario autenticado no es un barbero");
        }
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

    private AppUser getCurrentBarber() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || auth.getPrincipal() == null) {
            throw new RuntimeException("Usuario no autenticado");
        }

        Long userId;
        try {
            userId = Long.valueOf(auth.getPrincipal().toString());
        } catch (Exception e) {
            throw new RuntimeException("No se pudo obtener el userId del token");
        }

        return userRepository.findByIdWithTenant(userId)
                .orElseThrow(() -> new RuntimeException("Barbero no encontrado"));
    }

    private Long getCurrentTenantId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || auth.getDetails() == null) {
            throw new RuntimeException("No se pudo obtener el tenant del usuario autenticado");
        }

        Object details = auth.getDetails();

        if (!(details instanceof Map<?, ?> map)) {
            throw new RuntimeException("Los detalles de autenticación no contienen el tenantId");
        }

        Object tenantIdValue = map.get("tenantId");

        if (tenantIdValue == null) {
            throw new RuntimeException("tenantId no encontrado en el token");
        }

        try {
            return Long.valueOf(tenantIdValue.toString());
        } catch (Exception e) {
            throw new RuntimeException("tenantId inválido en el token");
        }
    }

    private Long getCurrentBranchId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || auth.getDetails() == null) {
            throw new RuntimeException("No se pudo obtener la sucursal del usuario autenticado");
        }

        Object details = auth.getDetails();

        if (!(details instanceof Map<?, ?> map)) {
            throw new RuntimeException("Los detalles de autenticación no contienen el branchId");
        }

        Object branchIdValue = map.get("branchId");

        if (branchIdValue == null) {
            throw new RuntimeException("branchId no encontrado en el token");
        }

        try {
            return Long.valueOf(branchIdValue.toString());
        } catch (Exception e) {
            throw new RuntimeException("branchId inválido en el token");
        }
    }
}
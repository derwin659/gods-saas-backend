package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.response.BarberCommissionItem;
import com.gods.saas.domain.dto.response.BarberCommissionResponse;
import com.gods.saas.domain.model.AppUser;
import com.gods.saas.domain.model.TenantSettings;
import com.gods.saas.domain.repository.AppUserRepository;
import com.gods.saas.domain.repository.SaleRepository;
import com.gods.saas.domain.repository.TenantSettingsRepository;
import com.gods.saas.domain.repository.projection.BarberCommissionDailyProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BarberCommissionService {

    private static final String DEFAULT_TIMEZONE = "America/Lima";

    private final SaleRepository saleRepository;
    private final AppUserRepository appUserRepository;
    private final TenantSettingsRepository tenantSettingsRepository;

    public BarberCommissionResponse getCommissions(LocalDate from, LocalDate to) {
        AppUser barber = getCurrentBarber();
        Long tenantId = getCurrentTenantId();
        Long branchId = getCurrentBranchId();

        ZoneId tenantZone = resolveTenantZoneId(tenantId);
        LocalDate today = LocalDate.now(tenantZone);

        if (from == null && to == null) {
            from = today;
            to = today;
        } else if (from == null) {
            from = to;
        } else if (to == null) {
            to = from;
        }

        if (from.isAfter(to)) {
            throw new IllegalArgumentException("La fecha inicial no puede ser mayor que la fecha final");
        }

        BigDecimal porcentajeComision = resolveCommissionPercentage(barber);

        // Día del tenant -> convertido a UTC para consultar en BD
        LocalDateTime start = from.atStartOfDay(tenantZone)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();

        LocalDateTime end = to.plusDays(1).atStartOfDay(tenantZone)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();

        List<BarberCommissionDailyProjection> rows =
                saleRepository.findDailySalesByBarber(
                        tenantId,
                        branchId,
                        barber.getId(),
                        start,
                        end
                );

        List<BarberCommissionItem> items = rows.stream()
                .map(row -> {
                    BigDecimal ventas = nvl(row.getVentas());
                    BigDecimal comision = calculateCommission(ventas, porcentajeComision);

                    return BarberCommissionItem.builder()
                            .fecha(row.getFecha())
                            .ventas(ventas.setScale(2, RoundingMode.HALF_UP))
                            .comision(comision.setScale(2, RoundingMode.HALF_UP))
                            .build();
                })
                .toList();

        BigDecimal totalVentas = items.stream()
                .map(BarberCommissionItem::getVentas)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalComision = items.stream()
                .map(BarberCommissionItem::getComision)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        return BarberCommissionResponse.builder()
                .barberName(buildFullName(barber))
                .from(from)
                .to(to)
                .totalVentas(totalVentas)
                .totalComision(totalComision)
                .porcentajeComision(
                        porcentajeComision == null
                                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                                : porcentajeComision.setScale(2, RoundingMode.HALF_UP)
                )
                .items(items)
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

    private BigDecimal calculateCommission(BigDecimal ventas, BigDecimal porcentajeComision) {
        if (porcentajeComision == null || porcentajeComision.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return ventas
                .multiply(porcentajeComision)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String buildFullName(AppUser user) {
        String nombre = user.getNombre() == null ? "" : user.getNombre().trim();
        String apellido = user.getApellido() == null ? "" : user.getApellido().trim();
        return (nombre + " " + apellido).trim();
    }

    private BigDecimal resolveCommissionPercentage(AppUser barber) {
        if (barber == null) {
            return BigDecimal.ZERO;
        }

        if (Boolean.TRUE.equals(barber.getSalaryMode())) {
            return BigDecimal.ZERO;
        }

        return barber.getCommissionPercentage() == null
                ? BigDecimal.ZERO
                : barber.getCommissionPercentage();
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

        return appUserRepository.findById(userId)
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
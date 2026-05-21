package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.response.BarberAdvanceDetailResponse;
import com.gods.saas.domain.dto.response.BarberCommissionItem;
import com.gods.saas.domain.dto.response.BarberCommissionResponse;
import com.gods.saas.domain.model.AppUser;
import com.gods.saas.domain.model.CashMovement;
import com.gods.saas.domain.model.TenantSettings;
import com.gods.saas.domain.repository.AppUserRepository;
import com.gods.saas.domain.repository.BarberPaymentRepository;
import com.gods.saas.domain.repository.CashMovementRepository;
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
    private final CashMovementRepository cashMovementRepository;
    private final BarberPaymentRepository barberPaymentRepository;

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

        // Mantiene el comportamiento actual para ventas/comisiones por fecha.
        // Día del tenant -> convertido a UTC para consultar ventas en BD.
        LocalDateTime saleStart = from.atStartOfDay(tenantZone)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();

        LocalDateTime saleEnd = to.plusDays(1).atStartOfDay(tenantZone)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();

        // Para movimientos de caja se usa el rango contable local del tenant,
        // igual que el preview de pagos de barbero.
        LocalDateTime movementStart = from.atStartOfDay();
        LocalDateTime movementEnd = to.plusDays(1).atStartOfDay();

        List<BarberCommissionDailyProjection> rows =
                saleRepository.findDailySalesByBarber(
                        tenantId,
                        branchId,
                        barber.getId(),
                        saleStart,
                        saleEnd
                );

        List<BarberCommissionItem> items = rows.stream()
                .map(row -> {
                    BigDecimal ventas = nvl(row.getVentas()).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal comision = calculateCommission(ventas, porcentajeComision)
                            .setScale(2, RoundingMode.HALF_UP);

                    return BarberCommissionItem.builder()
                            .fecha(row.getFecha())
                            .ventas(ventas)
                            .comision(comision)
                            .build();
                })
                .toList();

        BigDecimal totalVentas = items.stream()
                .map(BarberCommissionItem::getVentas)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal serviceCommissionAmount = items.stream()
                .map(BarberCommissionItem::getComision)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal productCommissionAmount = nvl(
                saleRepository.sumBarberProductCommissionsByRange(
                        tenantId,
                        branchId,
                        barber.getId(),
                        saleStart,
                        saleEnd
                )
        ).setScale(2, RoundingMode.HALF_UP);

        BigDecimal tipsAmount = nvl(
                saleRepository.sumBarberTipsByRange(
                        tenantId,
                        branchId,
                        barber.getId(),
                        saleStart,
                        saleEnd
                )
        ).setScale(2, RoundingMode.HALF_UP);

        BigDecimal advancesApplied = nvl(
                cashMovementRepository.sumAdvancesByBarberAndRange(
                        tenantId,
                        branchId,
                        barber.getId(),
                        movementStart,
                        movementEnd
                )
        ).setScale(2, RoundingMode.HALF_UP);

        List<BarberAdvanceDetailResponse> advances =
                cashMovementRepository.findAdvancesByBarberAndRange(
                                tenantId,
                                branchId,
                                barber.getId(),
                                movementStart,
                                movementEnd
                        )
                        .stream()
                        .map(this::mapAdvance)
                        .toList();

        BigDecimal previousPaymentsApplied = nvl(
                barberPaymentRepository.sumPaidInPeriod(
                        tenantId,
                        branchId,
                        barber.getId(),
                        from,
                        to
                )
        ).setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalComision = serviceCommissionAmount
                .add(productCommissionAmount)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal grossAmount = totalComision
                .add(tipsAmount)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal pendingAmount = grossAmount
                .subtract(advancesApplied)
                .subtract(previousPaymentsApplied)
                .setScale(2, RoundingMode.HALF_UP);

        if (pendingAmount.compareTo(BigDecimal.ZERO) < 0) {
            pendingAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

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
                .baseSales(totalVentas)
                .serviceCommissionAmount(serviceCommissionAmount)
                .productCommissionAmount(productCommissionAmount)
                .tipsAmount(tipsAmount)
                .grossAmount(grossAmount)
                .advancesApplied(advancesApplied)
                .previousPaymentsApplied(previousPaymentsApplied)
                .pendingAmount(pendingAmount)
                .advances(advances)
                .items(items)
                .build();
    }

    private BarberAdvanceDetailResponse mapAdvance(CashMovement cm) {
        return BarberAdvanceDetailResponse.builder()
                .movementId(cm.getId())
                .movementDate(cm.getMovementDate())
                .amount(nvl(cm.getAmount()).setScale(2, RoundingMode.HALF_UP))
                .concept(cm.getConcept())
                .note(cm.getNote())
                .paymentMethod(cm.getPaymentMethod() != null ? cm.getPaymentMethod().name() : null)
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
        String fullName = (nombre + " " + apellido).trim();
        return fullName.isBlank() ? "Barbero" : fullName;
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

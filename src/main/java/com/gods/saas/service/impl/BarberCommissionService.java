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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

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

        if (from.isAfter(to)) {
            return BarberCommissionResponse.builder()
                    .barberName(buildFullName(barber))
                    .from(from)
                    .to(to)
                    .totalVentas(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                    .totalComision(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                    .porcentajeComision(
                            porcentajeComision == null
                                    ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                                    : porcentajeComision.setScale(2, RoundingMode.HALF_UP)
                    )
                    .baseSales(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                    .serviceCommissionAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                    .productCommissionAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                    .tipsAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                    .grossAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                    .advancesApplied(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                    .previousPaymentsApplied(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                    .pendingAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                    .advances(List.of())
                    .items(List.of())
                    .build();
        }

        // Ventas por día del tenant convertidas a UTC para consultar BD.
        LocalDateTime saleStart = from.atStartOfDay(tenantZone)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();

        LocalDateTime saleEnd = to.plusDays(1).atStartOfDay(tenantZone)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();

        // Movimientos de caja por fecha contable/local.
        LocalDateTime movementStart = from.atStartOfDay();
        LocalDateTime movementEnd = to.plusDays(1).atStartOfDay();

        List<BarberAdvanceDetailResponse> advancesInRange = cashMovementRepository
                .findAdvancesByBarberAndRange(
                        tenantId,
                        branchId,
                        barber.getId(),
                        movementStart,
                        movementEnd
                )
                .stream()
                .map(this::mapAdvance)
                .toList();

        List<BarberCommissionDailyProjection> rows = saleRepository.findDailySalesByBarber(
                tenantId,
                branchId,
                barber.getId(),
                saleStart,
                saleEnd
        );

        boolean salaryMode = Boolean.TRUE.equals(barber.getSalaryMode());

        Map<LocalDate, BigDecimal> salesByDate = rows.stream()
                .collect(java.util.stream.Collectors.toMap(
                        BarberCommissionDailyProjection::getFecha,
                        row -> nvl(row.getVentas()),
                        BigDecimal::add
                ));

        TreeSet<LocalDate> datesToShow = new TreeSet<>(salesByDate.keySet());
        LocalDate periodFrom = from;
        LocalDate periodTo = to;
        advancesInRange.stream()
                .map(BarberAdvanceDetailResponse::getMovementDate)
                .filter(date -> date != null)
                .map(LocalDateTime::toLocalDate)
                .filter(date -> !date.isBefore(periodFrom) && !date.isAfter(periodTo))
                .forEach(datesToShow::add);

        List<BarberCommissionItem> items = datesToShow.stream()
                .map(date -> buildDailyItem(
                        tenantId,
                        branchId,
                        barber.getId(),
                        barber,
                        tenantZone,
                        date,
                        salesByDate.getOrDefault(date, BigDecimal.ZERO),
                        porcentajeComision,
                        advancesInRange
                ))
                .toList();

        if (salaryMode) {
            items = buildSalaryItems(
                    tenantId,
                    branchId,
                    barber,
                    tenantZone,
                    from,
                    to,
                    rows,
                    advancesInRange
            );
        }

        BigDecimal totalVentas = items.stream()
                .map(BarberCommissionItem::getVentas)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal serviceCommissionAmount = items.stream()
                .map(BarberCommissionItem::getServiceCommissionAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal productCommissionAmount = items.stream()
                .map(BarberCommissionItem::getProductCommissionAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal tipsAmount = items.stream()
                .map(BarberCommissionItem::getTipsAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal advancesApplied = items.stream()
                .map(BarberCommissionItem::getAdvancesApplied)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal previousPaymentsApplied = items.stream()
                .map(BarberCommissionItem::getPreviousPaymentsApplied)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalComision = serviceCommissionAmount
                .add(productCommissionAmount)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal grossAmount = totalComision
                .add(tipsAmount)
                .setScale(2, RoundingMode.HALF_UP);

        // IMPORTANTE:
        // pendingAmount se calcula como suma de pendientes diarios, no como
        // gross total - pagos solapados totales. Esto evita que un pago de un
        // periodo anterior (por ejemplo 17-19) deje en cero el día actual al
        // consultar un rango amplio (por ejemplo 19-20).
        BigDecimal pendingAmount = items.stream()
                .map(BarberCommissionItem::getPendingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        LocalDate effectiveFrom = resolveEffectiveFrom(tenantId, branchId, barber.getId(), from, to);
        if (effectiveFrom.isAfter(to)) {
            previousPaymentsApplied = nvl(
                    barberPaymentRepository.sumPaidInPeriod(
                            tenantId,
                            branchId,
                            barber.getId(),
                            from,
                            to
                    )
            ).setScale(2, RoundingMode.HALF_UP);
            totalVentas = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            serviceCommissionAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            productCommissionAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            tipsAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            advancesApplied = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            totalComision = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            grossAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            pendingAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        } else {
            LocalDateTime summaryStart = effectiveFrom.atStartOfDay();
            LocalDateTime summaryEnd = to.plusDays(1).atStartOfDay();

            totalVentas = nvl(
                    saleRepository.sumBarberItemSalesByRange(
                            tenantId,
                            branchId,
                            barber.getId(),
                            summaryStart,
                            summaryEnd
                    )
            ).setScale(2, RoundingMode.HALF_UP);

            productCommissionAmount = nvl(
                    saleRepository.sumBarberProductCommissionsByRange(
                            tenantId,
                            branchId,
                            barber.getId(),
                            summaryStart,
                            summaryEnd
                    )
            ).setScale(2, RoundingMode.HALF_UP);

            tipsAmount = nvl(
                    saleRepository.sumBarberTipsByRange(
                            tenantId,
                            branchId,
                            barber.getId(),
                            summaryStart,
                            summaryEnd
                    )
            ).setScale(2, RoundingMode.HALF_UP);

            advancesApplied = nvl(
                    cashMovementRepository.sumAdvancesByBarberAndRange(
                            tenantId,
                            branchId,
                            barber.getId(),
                            summaryStart,
                            summaryEnd
                    )
            ).setScale(2, RoundingMode.HALF_UP);

            previousPaymentsApplied = nvl(
                    barberPaymentRepository.sumPaidInPeriod(
                            tenantId,
                            branchId,
                            barber.getId(),
                            effectiveFrom,
                            to
                    )
            ).setScale(2, RoundingMode.HALF_UP);

            BigDecimal salaryAmount = salaryMode
                    ? resolveSalaryAmountForPeriod(barber, effectiveFrom, to)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

            serviceCommissionAmount = salaryMode
                    ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                    : calculateCommission(totalVentas, porcentajeComision)
                    .setScale(2, RoundingMode.HALF_UP);

            totalComision = serviceCommissionAmount
                    .add(productCommissionAmount)
                    .setScale(2, RoundingMode.HALF_UP);

            grossAmount = (salaryMode ? salaryAmount.add(productCommissionAmount) : totalComision)
                    .add(tipsAmount)
                    .setScale(2, RoundingMode.HALF_UP);

            pendingAmount = grossAmount
                    .subtract(advancesApplied)
                    .subtract(previousPaymentsApplied)
                    .setScale(2, RoundingMode.HALF_UP);

            if (pendingAmount.compareTo(BigDecimal.ZERO) < 0) {
                pendingAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            }
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
                .advances(advancesInRange)
                .items(items)
                .build();
    }

    private BarberCommissionItem buildDailyItem(
            Long tenantId,
            Long branchId,
            Long barberUserId,
            AppUser barber,
            ZoneId tenantZone,
            LocalDate fecha,
            BigDecimal baseSales,
            BigDecimal porcentajeComision,
            List<BarberAdvanceDetailResponse> advancesInRange
    ) {
        boolean salaryMode = Boolean.TRUE.equals(barber.getSalaryMode());
        BigDecimal dayBaseSales = nvl(baseSales).setScale(2, RoundingMode.HALF_UP);
        BigDecimal serviceCommission = salaryMode
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : calculateCommission(dayBaseSales, porcentajeComision)
                .setScale(2, RoundingMode.HALF_UP);

        LocalDateTime saleStart = fecha.atStartOfDay(tenantZone)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
        LocalDateTime saleEnd = fecha.plusDays(1).atStartOfDay(tenantZone)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();

        BigDecimal productCommission = nvl(
                saleRepository.sumBarberProductCommissionsByRange(
                        tenantId,
                        branchId,
                        barberUserId,
                        saleStart,
                        saleEnd
                )
        ).setScale(2, RoundingMode.HALF_UP);

        BigDecimal tips = nvl(
                saleRepository.sumBarberTipsByRange(
                        tenantId,
                        branchId,
                        barberUserId,
                        saleStart,
                        saleEnd
                )
        ).setScale(2, RoundingMode.HALF_UP);

        LocalDateTime movementStart = fecha.atStartOfDay();
        LocalDateTime movementEnd = fecha.plusDays(1).atStartOfDay();

        BigDecimal advancesApplied = nvl(
                cashMovementRepository.sumAdvancesByBarberAndRange(
                        tenantId,
                        branchId,
                        barberUserId,
                        movementStart,
                        movementEnd
                )
        ).setScale(2, RoundingMode.HALF_UP);

        List<BarberAdvanceDetailResponse> dayAdvances = advancesInRange.stream()
                .filter(a -> a.getMovementDate() != null && fecha.equals(a.getMovementDate().toLocalDate()))
                .toList();

        BigDecimal rawPreviousPayments = nvl(
                barberPaymentRepository.sumPaidInPeriod(
                        tenantId,
                        branchId,
                        barberUserId,
                        fecha,
                        fecha
                )
        ).setScale(2, RoundingMode.HALF_UP);

        BigDecimal commission = serviceCommission
                .add(productCommission)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal salaryAmount = salaryMode
                ? resolveSalaryAmountForPeriod(barber, fecha, fecha)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        BigDecimal gross = (salaryMode ? salaryAmount.add(productCommission) : commission)
                .add(tips)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal maxPreviousPaymentForDay = gross
                .subtract(advancesApplied)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        // Evita sobre-descontar pagos que corresponden a periodos solapados.
        // Si un pago cubre varios días, el día consultado nunca debe descontar
        // más de lo generado en ese día luego de adelantos.
        BigDecimal previousPayments = rawPreviousPayments
                .min(maxPreviousPaymentForDay)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal pending = gross
                .subtract(advancesApplied)
                .subtract(previousPayments)
                .setScale(2, RoundingMode.HALF_UP);

        if (pending.compareTo(BigDecimal.ZERO) < 0) {
            pending = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return BarberCommissionItem.builder()
                .fecha(fecha)
                .ventas(dayBaseSales)
                .comision(commission)
                .baseSales(dayBaseSales)
                .serviceCommissionAmount(serviceCommission)
                .productCommissionAmount(productCommission)
                .tipsAmount(tips)
                .grossAmount(gross)
                .advancesApplied(advancesApplied)
                .previousPaymentsApplied(previousPayments)
                .pendingAmount(pending)
                .advances(dayAdvances)
                .build();
    }

    private List<BarberCommissionItem> buildSalaryItems(
            Long tenantId,
            Long branchId,
            AppUser barber,
            ZoneId tenantZone,
            LocalDate from,
            LocalDate to,
            List<BarberCommissionDailyProjection> rows,
            List<BarberAdvanceDetailResponse> advancesInRange
    ) {
        Map<LocalDate, BigDecimal> salesByDate = rows.stream()
                .collect(java.util.stream.Collectors.toMap(
                        BarberCommissionDailyProjection::getFecha,
                        row -> nvl(row.getVentas()),
                        BigDecimal::add
                ));

        return from.datesUntil(to.plusDays(1))
                .map(date -> buildDailyItem(
                        tenantId,
                        branchId,
                        barber.getId(),
                        barber,
                        tenantZone,
                        date,
                        salesByDate.getOrDefault(date, BigDecimal.ZERO),
                        BigDecimal.ZERO,
                        advancesInRange
                ))
                .toList();
    }

    private LocalDate resolveEffectiveFrom(
            Long tenantId,
            Long branchId,
            Long barberUserId,
            LocalDate from,
            LocalDate to
    ) {
        return barberPaymentRepository
                .findLatestPaidPeriodToOverlapping(tenantId, branchId, barberUserId, from, to)
                .map(latestPaidPeriodTo -> {
                    LocalDate nextUnpaidDay = latestPaidPeriodTo.plusDays(1);
                    return nextUnpaidDay.isAfter(from) ? nextUnpaidDay : from;
                })
                .orElse(from);
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

    private BigDecimal resolveSalaryAmountForPeriod(AppUser barber, LocalDate periodFrom, LocalDate periodTo) {
        BigDecimal fixedSalaryAmount = nvl(barber.getFixedSalaryAmount());
        if (fixedSalaryAmount.compareTo(BigDecimal.ZERO) <= 0 || barber.getSalaryFrequency() == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return switch (barber.getSalaryFrequency()) {
            case WEEKLY -> fixedSalaryAmount
                    .divide(BigDecimal.valueOf(7), 2, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(ChronoUnit.DAYS.between(periodFrom, periodTo) + 1))
                    .setScale(2, RoundingMode.HALF_UP);
            case BIWEEKLY -> fixedSalaryAmount
                    .divide(BigDecimal.valueOf(15), 2, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(ChronoUnit.DAYS.between(periodFrom, periodTo) + 1))
                    .setScale(2, RoundingMode.HALF_UP);
            case MONTHLY -> prorateMonthlySalary(fixedSalaryAmount, periodFrom, periodTo);
        };
    }

    private BigDecimal prorateMonthlySalary(BigDecimal monthlyAmount, LocalDate periodFrom, LocalDate periodTo) {
        LocalDate monthStart = periodFrom.withDayOfMonth(1);
        LocalDate monthEnd = periodFrom.withDayOfMonth(periodFrom.lengthOfMonth());

        if (periodFrom.equals(monthStart) && periodTo.equals(monthEnd)) {
            return monthlyAmount.setScale(2, RoundingMode.HALF_UP);
        }

        long daysInRange = ChronoUnit.DAYS.between(periodFrom, periodTo) + 1;
        long daysInMonth = periodFrom.lengthOfMonth();

        return monthlyAmount
                .multiply(BigDecimal.valueOf(daysInRange))
                .divide(BigDecimal.valueOf(daysInMonth), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String buildFullName(AppUser user) {
        String nombre = user.getNombre() == null ? "" : user.getNombre().trim();
        String apellido = user.getApellido() == null ? "" : user.getApellido().trim();
        String fullName = (nombre + " " + apellido).trim();
        return fullName.isBlank() ? "Profesional" : fullName;
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
                .orElseThrow(() -> new RuntimeException("Profesional no encontrado"));
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

package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.CreateBarberPaymentRequest;
import com.gods.saas.domain.dto.response.BarberPaymentPreviewResponse;
import com.gods.saas.domain.dto.response.BarberPaymentResponse;
import com.gods.saas.domain.enums.*;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import com.gods.saas.service.impl.impl.BarberPaymentService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional
public class BarberPaymentServiceImpl implements BarberPaymentService {

    private static final String DEFAULT_TIMEZONE = "America/Lima";

    private final BarberPaymentRepository barberPaymentRepository;
    private final CashRegisterRepository cashRegisterRepository;
    private final CashMovementRepository cashMovementRepository;
    private final SaleRepository saleRepository;
    private final AppUserRepository appUserRepository;
    private final UserTenantRoleRepository userTenantRoleRepository;
    private final TenantSettingsRepository tenantSettingsRepository;

    @Override
    @Transactional(readOnly = true)
    public BarberPaymentPreviewResponse preview(
            Long tenantId,
            Long branchId,
            Long barberUserId,
            LocalDate periodFrom,
            LocalDate periodTo
    ) {
        validatePeriod(periodFrom, periodTo);
        AppUser barber = resolveBarber(tenantId, branchId, barberUserId);

        LocalDateTime start = periodFrom.atStartOfDay();
        LocalDateTime end = periodTo.plusDays(1).atStartOfDay();

        boolean salaryMode = Boolean.TRUE.equals(barber.getSalaryMode());
        BigDecimal salesBase = safe(
                saleRepository.sumBarberItemSalesByRange(
                        tenantId, branchId, barberUserId, start, end
                )
        );

        BigDecimal commissionPercentage = safe(barber.getCommissionPercentage());
        BigDecimal commissionAmount = salaryMode
                ? BigDecimal.ZERO
                : salesBase.multiply(commissionPercentage)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        BigDecimal advances = safe(
                cashMovementRepository.sumAdvancesByBarberAndRange(
                        tenantId, branchId, barberUserId, start, end
                )
        );

        BigDecimal previousPayments = safe(
                barberPaymentRepository.sumPaidInPeriod(
                        tenantId, branchId, barberUserId, periodFrom, periodTo
                )
        );

        BigDecimal salaryAmount = salaryMode
                ? resolveSalaryAmountForPeriod(barber, periodFrom, periodTo)
                : BigDecimal.ZERO;

        BigDecimal gross = salaryMode ? salaryAmount : commissionAmount;
        BigDecimal pending = gross.subtract(advances).subtract(previousPayments);
        if (pending.compareTo(BigDecimal.ZERO) < 0) {
            pending = BigDecimal.ZERO;
        }

        return BarberPaymentPreviewResponse.builder()
                .barberUserId(barber.getId())
                .barberName(fullName(barber))
                .paymentMode(salaryMode ? "SALARY" : "COMMISSION")
                .periodFrom(periodFrom)
                .periodTo(periodTo)
                .baseSales(salesBase)
                .percentageApplied(salaryMode ? null : commissionPercentage)
                .commissionAmount(commissionAmount)
                .salaryAmount(salaryAmount)
                .advancesApplied(advances)
                .previousPaymentsApplied(previousPayments)
                .pendingAmount(pending)
                .build();
    }

    @Override
    public BarberPaymentResponse createPayment(
            Long tenantId,
            Long branchId,
            Long cashRegisterId,
            Long actorUserId,
            CreateBarberPaymentRequest request
    ) {
        validateActor(actorUserId, tenantId);
        validatePeriod(request.getPeriodFrom(), request.getPeriodTo());

        CashRegister cashRegister = cashRegisterRepository
                .findByIdAndTenant_Id(cashRegisterId, tenantId)
                .orElseThrow(() -> new IllegalStateException("Caja no encontrada."));

        if (!cashRegister.getBranch().getId().equals(branchId)) {
            throw new IllegalStateException("La caja no pertenece a esta sede.");
        }

        if (cashRegister.getStatus() != CashRegisterStatus.OPEN) {
            throw new IllegalStateException("Solo puedes pagar barberos con caja abierta.");
        }

        AppUser actor = appUserRepository.findByIdAndTenant_Id(actorUserId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Usuario actor no encontrado."));

        AppUser barber = resolveBarber(tenantId, branchId, request.getBarberUserId());

        BarberPaymentPreviewResponse preview = preview(
                tenantId,
                branchId,
                barber.getId(),
                request.getPeriodFrom(),
                request.getPeriodTo()
        );

        BigDecimal amountPaid = safe(request.getAmountPaid());
        if (amountPaid.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("El monto a pagar debe ser mayor a cero.");
        }

        if (amountPaid.compareTo(preview.getPendingAmount()) > 0) {
            throw new IllegalStateException("El monto excede el pendiente calculado.");
        }

        PaymentMethod paymentMethod = request.getPaymentMethod() == null
                ? PaymentMethod.CASH
                : request.getPaymentMethod();

        String concept = "Pago barbero " + fullName(barber)
                + " [" + request.getPeriodFrom() + " a " + request.getPeriodTo() + "]";

        ZoneId zoneId = getZoneIdForTenant(tenantId);
        LocalDateTime now = LocalDateTime.now(zoneId);

        CashMovement movement = CashMovement.builder()
                .tenant(cashRegister.getTenant())
                .branch(cashRegister.getBranch())
                .cashRegister(cashRegister)
                .user(actor)
                .barberUser(barber)
                .type(CashMovementType.PAYMENT_BARBER)
                .paymentMethod(paymentMethod)
                .amount(amountPaid)
                .concept(concept)
                .note(trimToNull(request.getNote()))
                .movementDate(now)
                .createdAt(now)
                .build();

        movement = cashMovementRepository.save(movement);

        BigDecimal remaining = preview.getPendingAmount().subtract(amountPaid);
        BarberPaymentStatus status = remaining.compareTo(BigDecimal.ZERO) == 0
                ? BarberPaymentStatus.PAID
                : BarberPaymentStatus.PARTIAL;

        BarberPayment entity = BarberPayment.builder()
                .tenant(cashRegister.getTenant())
                .branch(cashRegister.getBranch())
                .cashRegister(cashRegister)
                .barberUser(barber)
                .registeredByUser(actor)
                .paymentMode(BarberPaymentMode.valueOf(preview.getPaymentMode()))
                .salaryAmount(preview.getSalaryAmount() == null ? BigDecimal.ZERO : preview.getSalaryAmount())
                .status(status)
                .periodFrom(request.getPeriodFrom())
                .periodTo(request.getPeriodTo())
                .baseAmount(preview.getBaseSales() == null ? BigDecimal.ZERO : preview.getBaseSales())
                .percentageApplied(preview.getPercentageApplied())
                .commissionAmount(preview.getCommissionAmount() == null ? BigDecimal.ZERO : preview.getCommissionAmount())
                .advancesApplied(preview.getAdvancesApplied() == null ? BigDecimal.ZERO : preview.getAdvancesApplied())
                .previousPaymentsApplied(preview.getPreviousPaymentsApplied() == null ? BigDecimal.ZERO : preview.getPreviousPaymentsApplied())
                .amountPaid(amountPaid)
                .remainingAmount(remaining)
                .paymentMethod(paymentMethod)
                .cashMovement(movement)
                .concept(concept)
                .note(trimToNull(request.getNote()))
                .createdAt(now)
                .build();

        entity = barberPaymentRepository.save(entity);

        return map(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BarberPaymentResponse> history(Long tenantId, Long branchId, Long barberUserId) {
        return barberPaymentRepository
                .findByTenant_IdAndBranch_IdAndBarberUser_IdOrderByCreatedAtDesc(
                        tenantId, branchId, barberUserId
                )
                .stream()
                .map(this::map)
                .toList();
    }

    private void validateActor(Long actorUserId, Long tenantId) {
        boolean allowed = userTenantRoleRepository.existsByUserIdAndTenantIdAndRoleIn(
                actorUserId,
                tenantId,
                List.of(RoleType.OWNER, RoleType.ADMIN)
        );

        if (!allowed) {
            throw new IllegalStateException("Solo OWNER o ADMIN pueden pagar a barberos.");
        }
    }

    private AppUser resolveBarber(Long tenantId, Long branchId, Long barberUserId) {
        if (barberUserId == null) {
            throw new IllegalStateException("Debes seleccionar un barbero.");
        }

        AppUser barber = appUserRepository.findByIdAndTenant_Id(barberUserId, tenantId)
                .orElseThrow(() -> new IllegalStateException("Barbero no encontrado."));

        String role = barber.getRol() == null ? "" : barber.getRol().trim().toUpperCase(Locale.ROOT);
        if (!"BARBER".equals(role)) {
            throw new IllegalStateException("El usuario seleccionado no es un barbero válido.");
        }

        if (barber.getBranch() != null && !barber.getBranch().getId().equals(branchId)) {
            throw new IllegalStateException("El barbero no pertenece a esta sede.");
        }

        return barber;
    }

    private void validatePeriod(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalStateException("Debes enviar el rango del pago.");
        }
        if (to.isBefore(from)) {
            throw new IllegalStateException("El rango es inválido.");
        }
    }

    private BarberPaymentResponse map(BarberPayment bp) {
        return BarberPaymentResponse.builder()
                .paymentId(bp.getId())
                .barberUserId(bp.getBarberUser().getId())
                .barberName(fullName(bp.getBarberUser()))
                .paymentMode(bp.getPaymentMode().name())
                .status(bp.getStatus().name())
                .periodFrom(bp.getPeriodFrom())
                .periodTo(bp.getPeriodTo())
                .baseAmount(safe(bp.getBaseAmount()))
                .percentageApplied(bp.getPercentageApplied())
                .commissionAmount(safe(bp.getCommissionAmount()))
                .advancesApplied(safe(bp.getAdvancesApplied()))
                .previousPaymentsApplied(safe(bp.getPreviousPaymentsApplied()))
                .amountPaid(safe(bp.getAmountPaid()))
                .remainingAmount(safe(bp.getRemainingAmount()))
                .paymentMethod(bp.getPaymentMethod().name())
                .cashMovementId(bp.getCashMovement() != null ? bp.getCashMovement().getId() : null)
                .concept(bp.getConcept())
                .note(bp.getNote())
                .createdAt(bp.getCreatedAt())
                .build();
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String fullName(AppUser user) {
        String nombre = user.getNombre() == null ? "" : user.getNombre().trim();
        String apellido = user.getApellido() == null ? "" : user.getApellido().trim();
        String full = (nombre + " " + apellido).trim();
        return full.isBlank() ? "Barbero" : full;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private BigDecimal resolveSalaryAmountForPeriod(AppUser barber, LocalDate periodFrom, LocalDate periodTo) {
        BigDecimal fixedSalaryAmount = safe(barber.getFixedSalaryAmount());
        if (fixedSalaryAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("El barbero no tiene sueldo fijo configurado.");
        }

        if (barber.getSalaryFrequency() == null) {
            throw new IllegalStateException("El barbero no tiene periodicidad de sueldo configurada.");
        }

        return switch (barber.getSalaryFrequency()) {
            case WEEKLY -> fixedSalaryAmount;
            case BIWEEKLY -> fixedSalaryAmount;
            case MONTHLY -> prorateMonthlySalary(fixedSalaryAmount, periodFrom, periodTo);
        };
    }

    private BigDecimal prorateMonthlySalary(BigDecimal monthlyAmount, LocalDate periodFrom, LocalDate periodTo) {
        LocalDate monthStart = periodFrom.withDayOfMonth(1);
        LocalDate monthEnd = periodFrom.withDayOfMonth(periodFrom.lengthOfMonth());

        if (periodFrom.equals(monthStart) && periodTo.equals(monthEnd)) {
            return monthlyAmount;
        }

        long daysInRange = java.time.temporal.ChronoUnit.DAYS.between(periodFrom, periodTo) + 1;
        long daysInMonth = periodFrom.lengthOfMonth();

        return monthlyAmount
                .multiply(BigDecimal.valueOf(daysInRange))
                .divide(BigDecimal.valueOf(daysInMonth), 2, RoundingMode.HALF_UP);
    }

    private void normalizeCompensationModel(AppUser user) {
        boolean salaryMode = Boolean.TRUE.equals(user.getSalaryMode());

        if (salaryMode) {
            if (user.getFixedSalaryAmount() == null || user.getFixedSalaryAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException("Debes configurar el sueldo fijo del barbero.");
            }
            if (user.getSalaryFrequency() == null) {
                throw new IllegalStateException("Debes configurar la periodicidad del sueldo.");
            }
            user.setCommissionPercentage(null);
        } else {
            if (user.getCommissionPercentage() == null || user.getCommissionPercentage().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException("Debes configurar el porcentaje de comisión.");
            }
            user.setFixedSalaryAmount(null);
            user.setSalaryFrequency(null);
            user.setSalaryStartDate(null);
        }
    }

    private ZoneId getZoneIdForTenant(Long tenantId) {
        String timezone = tenantSettingsRepository.findByTenant_Id(tenantId)
                .map(TenantSettings::getTimezone)
                .filter(tz -> tz != null && !tz.isBlank())
                .orElse(DEFAULT_TIMEZONE);

        try {
            return ZoneId.of(timezone);
        } catch (Exception e) {
            return ZoneId.of(DEFAULT_TIMEZONE);
        }
    }
}
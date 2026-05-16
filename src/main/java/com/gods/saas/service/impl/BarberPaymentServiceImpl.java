package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.CreateBarberPaymentRequest;
import com.gods.saas.domain.dto.response.BarberPaymentPreviewResponse;
import com.gods.saas.domain.dto.response.BarberPaymentResponse;
import com.gods.saas.domain.enums.*;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import com.gods.saas.service.impl.impl.BarberPaymentService;
import com.gods.saas.service.impl.impl.NotificationService;
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
    private final NotificationService notificationService;

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

        LocalDate effectivePeriodFrom = resolveEffectivePeriodFrom(
                tenantId, branchId, barberUserId, periodFrom, periodTo
        );

        if (effectivePeriodFrom.isAfter(periodTo)) {
            return emptyPreview(barber, periodFrom, periodTo);
        }

        LocalDateTime start = effectivePeriodFrom.atStartOfDay();
        LocalDateTime end = periodTo.plusDays(1).atStartOfDay();

        boolean salaryMode = Boolean.TRUE.equals(barber.getSalaryMode());
        BigDecimal salesBase = safe(
                saleRepository.sumBarberItemSalesByRange(
                        tenantId, branchId, barberUserId, start, end
                )
        );

        BigDecimal productCommissionsAmount = safe(
                saleRepository.sumBarberProductCommissionsByRange(
                        tenantId, branchId, barberUserId, start, end
                )
        );

        BigDecimal commissionPercentage = safe(barber.getCommissionPercentage());
        BigDecimal serviceCommissionAmount = salaryMode
                ? BigDecimal.ZERO
                : salesBase.multiply(commissionPercentage)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        BigDecimal commissionAmount = serviceCommissionAmount
                .add(productCommissionsAmount)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal tipsAmount = safe(
                saleRepository.sumBarberTipsByRange(
                        tenantId, branchId, barberUserId, start, end
                )
        );

        BigDecimal advances = safe(
                cashMovementRepository.sumAdvancesByBarberAndRange(
                        tenantId, branchId, barberUserId, start, end
                )
        );

        BigDecimal previousPayments = safe(
                barberPaymentRepository.sumPaidInPeriod(
                        tenantId, branchId, barberUserId, effectivePeriodFrom, periodTo
                )
        );

        BigDecimal salaryAmount = salaryMode
                ? resolveSalaryAmountForPeriod(barber, effectivePeriodFrom, periodTo)
                : BigDecimal.ZERO;

        BigDecimal gross = (salaryMode ? salaryAmount.add(productCommissionsAmount) : commissionAmount)
                .add(tipsAmount)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal pending = gross.subtract(advances).subtract(previousPayments);
        if (pending.compareTo(BigDecimal.ZERO) < 0) {
            pending = BigDecimal.ZERO;
        }

        return BarberPaymentPreviewResponse.builder()
                .barberUserId(barber.getId())
                .barberName(fullName(barber))
                .paymentMode(salaryMode ? "SALARY" : "COMMISSION")
                .periodFrom(effectivePeriodFrom)
                .periodTo(periodTo)
                .baseSales(salesBase)
                .percentageApplied(salaryMode ? null : commissionPercentage)
                .serviceCommissionAmount(serviceCommissionAmount)
                .commissionAmount(commissionAmount)
                .productCommissionAmount(productCommissionsAmount)
                .salaryAmount(salaryAmount)
                .tipsAmount(tipsAmount)
                .grossAmount(gross)
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

        List<PaymentSplit> paymentSplits = resolvePaymentSplits(request);
        BigDecimal amountPaid = paymentSplits.stream()
                .map(PaymentSplit::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        if (amountPaid.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("El monto a pagar debe ser mayor a cero.");
        }

        if (amountPaid.compareTo(preview.getPendingAmount()) > 0) {
            throw new IllegalStateException("El monto excede el pendiente calculado.");
        }

        String concept = "Pago barbero " + fullName(barber)
                + " [" + preview.getPeriodFrom() + " a " + preview.getPeriodTo() + "]";

        ZoneId zoneId = getZoneIdForTenant(tenantId);
        LocalDateTime now = LocalDateTime.now(zoneId);

        BigDecimal remainingAfterAll = preview.getPendingAmount().subtract(amountPaid).setScale(2, RoundingMode.HALF_UP);
        BarberPaymentStatus finalStatus = remainingAfterAll.compareTo(BigDecimal.ZERO) == 0
                ? BarberPaymentStatus.PAID
                : BarberPaymentStatus.PARTIAL;

        boolean mixedPayment = paymentSplits.size() > 1;
        BarberPayment lastSaved = null;
        BigDecimal accumulated = BigDecimal.ZERO;

        for (int i = 0; i < paymentSplits.size(); i++) {
            PaymentSplit split = paymentSplits.get(i);
            accumulated = accumulated.add(split.amount()).setScale(2, RoundingMode.HALF_UP);

            String splitConcept = mixedPayment
                    ? concept + " (" + paymentMethodLabel(split.paymentMethod()) + " S/ " + split.amount().setScale(2, RoundingMode.HALF_UP) + ")"
                    : concept;

            CashMovement movement = CashMovement.builder()
                    .tenant(cashRegister.getTenant())
                    .branch(cashRegister.getBranch())
                    .cashRegister(cashRegister)
                    .user(actor)
                    .barberUser(barber)
                    .type(CashMovementType.PAYMENT_BARBER)
                    .paymentMethod(split.paymentMethod())
                    .amount(split.amount())
                    .concept(splitConcept)
                    .note(trimToNull(request.getNote()))
                    .movementDate(now)
                    .createdAt(now)
                    .build();

            movement = cashMovementRepository.save(movement);

            BigDecimal remainingForThisSplit = preview.getPendingAmount()
                    .subtract(accumulated)
                    .max(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);

            BarberPaymentStatus statusForThisSplit = i == paymentSplits.size() - 1
                    ? finalStatus
                    : BarberPaymentStatus.PARTIAL;

            BarberPayment entity = BarberPayment.builder()
                    .tenant(cashRegister.getTenant())
                    .branch(cashRegister.getBranch())
                    .cashRegister(cashRegister)
                    .barberUser(barber)
                    .registeredByUser(actor)
                    .paymentMode(BarberPaymentMode.valueOf(preview.getPaymentMode()))
                    .salaryAmount(preview.getSalaryAmount() == null ? BigDecimal.ZERO : preview.getSalaryAmount())
                    .status(statusForThisSplit)
                    .periodFrom(preview.getPeriodFrom())
                    .periodTo(preview.getPeriodTo())
                    .baseAmount(preview.getBaseSales() == null ? BigDecimal.ZERO : preview.getBaseSales())
                    .percentageApplied(preview.getPercentageApplied())
                    .commissionAmount(preview.getCommissionAmount() == null ? BigDecimal.ZERO : preview.getCommissionAmount())
                    .advancesApplied(preview.getAdvancesApplied() == null ? BigDecimal.ZERO : preview.getAdvancesApplied())
                    .previousPaymentsApplied(preview.getPreviousPaymentsApplied() == null ? BigDecimal.ZERO : preview.getPreviousPaymentsApplied())
                    .amountPaid(split.amount())
                    .remainingAmount(remainingForThisSplit)
                    .paymentMethod(split.paymentMethod())
                    .cashMovement(movement)
                    .concept(splitConcept)
                    .note(trimToNull(request.getNote()))
                    .createdAt(now)
                    .build();

            lastSaved = barberPaymentRepository.save(entity);
        }

        if (lastSaved == null) {
            throw new IllegalStateException("No se pudo registrar el pago del barbero.");
        }

        notificationService.notifyBarberPaymentCreated(lastSaved);

        BarberPaymentResponse response = map(lastSaved);
        response.setAmountPaid(amountPaid);
        response.setRemainingAmount(remainingAfterAll);
        response.setStatus(finalStatus.name());
        response.setPaymentMethod(mixedPayment ? PaymentMethod.MIXED.name() : paymentSplits.get(0).paymentMethod().name());
        response.setCashMovementId(mixedPayment ? null : lastSaved.getCashMovement().getId());
        response.setConcept(concept);
        return response;
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


    private LocalDate resolveEffectivePeriodFrom(
            Long tenantId,
            Long branchId,
            Long barberUserId,
            LocalDate periodFrom,
            LocalDate periodTo
    ) {
        LocalDate latestPaidPeriodTo = barberPaymentRepository
                .findLatestPaidPeriodToOverlapping(tenantId, branchId, barberUserId, periodFrom, periodTo)
                .orElse(null);

        if (latestPaidPeriodTo == null) {
            return periodFrom;
        }

        LocalDate nextUnpaidDay = latestPaidPeriodTo.plusDays(1);
        return nextUnpaidDay.isAfter(periodFrom) ? nextUnpaidDay : periodFrom;
    }

    private BarberPaymentPreviewResponse emptyPreview(
            AppUser barber,
            LocalDate periodFrom,
            LocalDate periodTo
    ) {
        boolean salaryMode = Boolean.TRUE.equals(barber.getSalaryMode());
        BigDecimal commissionPercentage = salaryMode ? null : safe(barber.getCommissionPercentage());

        return BarberPaymentPreviewResponse.builder()
                .barberUserId(barber.getId())
                .barberName(fullName(barber))
                .paymentMode(salaryMode ? "SALARY" : "COMMISSION")
                .periodFrom(periodFrom)
                .periodTo(periodTo)
                .baseSales(BigDecimal.ZERO)
                .percentageApplied(commissionPercentage)
                .serviceCommissionAmount(BigDecimal.ZERO)
                .commissionAmount(BigDecimal.ZERO)
                .productCommissionAmount(BigDecimal.ZERO)
                .salaryAmount(BigDecimal.ZERO)
                .tipsAmount(BigDecimal.ZERO)
                .grossAmount(BigDecimal.ZERO)
                .advancesApplied(BigDecimal.ZERO)
                .previousPaymentsApplied(BigDecimal.ZERO)
                .pendingAmount(BigDecimal.ZERO)
                .build();
    }


    private List<PaymentSplit> resolvePaymentSplits(CreateBarberPaymentRequest request) {
        if (request.getPayments() != null && !request.getPayments().isEmpty()) {
            return request.getPayments().stream()
                    .map(item -> {
                        PaymentMethod method = parsePaymentMethod(item.getMethod());
                        BigDecimal amount = safe(item.getAmount()).setScale(2, RoundingMode.HALF_UP);
                        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                            throw new IllegalStateException("Cada método de pago debe tener un monto mayor a cero.");
                        }
                        return new PaymentSplit(method, amount);
                    })
                    .toList();
        }

        BigDecimal amount = safe(request.getAmountPaid()).setScale(2, RoundingMode.HALF_UP);
        PaymentMethod method = request.getPaymentMethod() == null ? PaymentMethod.CASH : request.getPaymentMethod();
        return List.of(new PaymentSplit(method, amount));
    }

    private PaymentMethod parsePaymentMethod(String rawMethod) {
        if (rawMethod == null || rawMethod.trim().isEmpty()) {
            return PaymentMethod.CASH;
        }

        String value = rawMethod.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "EFECTIVO" -> PaymentMethod.CASH;
            case "TARJETA" -> PaymentMethod.CARD;
            case "TRANSFERENCIA" -> PaymentMethod.TRANSFER;
            case "GRATIS" -> PaymentMethod.FREE;
            default -> {
                try {
                    yield PaymentMethod.valueOf(value);
                } catch (Exception e) {
                    throw new IllegalStateException("Método de pago inválido: " + rawMethod);
                }
            }
        };
    }

    private String paymentMethodLabel(PaymentMethod method) {
        if (method == null) return "Efectivo";
        return switch (method) {
            case CASH, EFECTIVO -> "Efectivo";
            case CARD, TARJETA -> "Tarjeta";
            case TRANSFER, TRANSFERENCIA -> "Transferencia";
            case YAPE -> "Yape";
            case PLIN -> "Plin";
            case NEQUI -> "Nequi";
            case DAVIPLATA -> "Daviplata";
            case PAGO_MOVIL -> "Pago móvil";
            case ZELLE -> "Zelle";
            case QR -> "QR";
            case FREE, GRATIS -> "Gratis";
            case DEPOSIT_APPLIED -> "Inicial aplicada";
            case MIXED -> "Mixto";
            case OTHER -> "Otro";
        };
    }

    private record PaymentSplit(PaymentMethod paymentMethod, BigDecimal amount) {
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
        BigDecimal tipsAmount = BigDecimal.ZERO;
        if (bp.getTenant() != null && bp.getBranch() != null && bp.getBarberUser() != null
                && bp.getPeriodFrom() != null && bp.getPeriodTo() != null) {
            LocalDateTime start = bp.getPeriodFrom().atStartOfDay();
            LocalDateTime end = bp.getPeriodTo().plusDays(1).atStartOfDay();
            tipsAmount = safe(saleRepository.sumBarberTipsByRange(
                    bp.getTenant().getId(),
                    bp.getBranch().getId(),
                    bp.getBarberUser().getId(),
                    start,
                    end
            ));
        }

        BigDecimal productCommissionsAmount = BigDecimal.ZERO;
        if (bp.getTenant() != null && bp.getBranch() != null && bp.getBarberUser() != null
                && bp.getPeriodFrom() != null && bp.getPeriodTo() != null) {
            LocalDateTime start = bp.getPeriodFrom().atStartOfDay();
            LocalDateTime end = bp.getPeriodTo().plusDays(1).atStartOfDay();
            productCommissionsAmount = safe(saleRepository.sumBarberProductCommissionsByRange(
                    bp.getTenant().getId(),
                    bp.getBranch().getId(),
                    bp.getBarberUser().getId(),
                    start,
                    end
            ));
        }

        BigDecimal serviceCommissionAmount = BigDecimal.ZERO;
        if (bp.getPaymentMode() != BarberPaymentMode.SALARY) {
            serviceCommissionAmount = safe(bp.getCommissionAmount())
                    .subtract(productCommissionsAmount)
                    .max(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal baseGross = bp.getPaymentMode() == BarberPaymentMode.SALARY
                ? safe(bp.getSalaryAmount()).add(safe(bp.getCommissionAmount()))
                : safe(bp.getCommissionAmount());
        BigDecimal grossAmount = baseGross.add(tipsAmount).setScale(2, RoundingMode.HALF_UP);

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
                .serviceCommissionAmount(serviceCommissionAmount)
                .commissionAmount(safe(bp.getCommissionAmount()))
                .productCommissionAmount(productCommissionsAmount)
                .tipsAmount(tipsAmount)
                .grossAmount(grossAmount)
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
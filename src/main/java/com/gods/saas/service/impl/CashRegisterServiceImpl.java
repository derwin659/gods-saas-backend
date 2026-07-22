package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.CashMovementRequest;
import com.gods.saas.domain.dto.request.CashFundMovementRequest;
import com.gods.saas.domain.dto.request.CloseCashRegisterRequest;
import com.gods.saas.domain.dto.request.OpenCashRegisterRequest;
import com.gods.saas.domain.dto.request.ReconcileCashRegisterRequest;
import com.gods.saas.domain.dto.response.CashMovementResponse;
import com.gods.saas.domain.dto.response.CashFundMovementResponse;
import com.gods.saas.domain.dto.response.CashFundSummaryResponse;
import com.gods.saas.domain.dto.response.CashAuditLogResponse;
import com.gods.saas.domain.dto.response.CashRegisterResponse;
import com.gods.saas.domain.enums.CashMovementType;
import com.gods.saas.domain.enums.CashFundingSource;
import com.gods.saas.domain.enums.CashFundMovementType;
import com.gods.saas.domain.enums.CashRegisterStatus;
import com.gods.saas.domain.enums.PaymentMethod;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import com.gods.saas.exception.GlobalExceptionHandler;
import com.gods.saas.service.impl.impl.CashRegisterService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.gods.saas.domain.dto.response.PaymentMethodSummaryResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
@Service
@RequiredArgsConstructor
@Transactional
public class CashRegisterServiceImpl implements CashRegisterService {

    private static final String DEFAULT_TIMEZONE = "America/Lima";

    private final CashRegisterRepository cashRegisterRepository;
    private final CashMovementRepository cashMovementRepository;
    private final CashFundMovementRepository cashFundMovementRepository;
    private final SaleRepository saleRepository;
    private final TenantRepository tenantRepository;
    private final BranchRepository branchRepository;
    private final AppUserRepository appUserRepository;
    private final TenantSettingsRepository tenantSettingsRepository;
    private final UserTenantRoleRepository userTenantRoleRepository;
    private final BarberPaymentRepository barberPaymentRepository;
    private final TenantPaymentMethodRepository tenantPaymentMethodRepository;
    private final CashAuditLogRepository cashAuditLogRepository;
    private final AdminPermissionService adminPermissionService;



    @Override
    public CashRegisterResponse open(Long tenantId, Long branchId, Long openedByUserId, OpenCashRegisterRequest request) {
        autoCloseOpenRegisterIfExpired(tenantId, branchId);

        if (cashRegisterRepository.existsByTenant_IdAndBranch_IdAndStatus(tenantId, branchId, CashRegisterStatus.OPEN)) {
            throw new IllegalStateException("Ya existe una caja abierta en esta sede.");
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant no encontrado"));

        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new EntityNotFoundException("Sede no encontrada"));

        AppUser openedBy = appUserRepository.findById(openedByUserId)
                .orElseThrow(() -> new EntityNotFoundException("Usuario aperturador no encontrado"));

        AppUser assignedUser = null;
        if (request.getAssignedUserId() != null) {
            assignedUser = appUserRepository.findById(request.getAssignedUserId())
                    .orElseThrow(() -> new EntityNotFoundException("Usuario asignado no encontrado"));
        }

        ZoneId zoneId = getZoneIdForTenant(tenantId);
        LocalDateTime now = LocalDateTime.now(zoneId);


        CashRegister cashRegister = CashRegister.builder()
                .tenant(tenant)
                .branch(branch)

                .openedByUser(openedBy)
                .assignedUser(assignedUser)
                .openingAmount(request.getOpeningAmount() == null ? BigDecimal.ZERO : request.getOpeningAmount())
                .status(CashRegisterStatus.OPEN)
                .openedAt(now)
                .openingNote(request.getOpeningNote())
                .build();

        cashRegisterRepository.save(cashRegister);

        BigDecimal fundWithdrawalAmount = safe(request.getFundWithdrawalAmount());
        if (fundWithdrawalAmount.compareTo(BigDecimal.ZERO) > 0) {
            adminPermissionService.checkOwnerOrAdminPermission("CASH_FUND_MANAGE");
            validateFundAvailable(branch, PaymentMethod.CASH, fundWithdrawalAmount);
            createFundMovementEntity(
                    tenant, branch, cashRegister, openedBy,
                    CashFundMovementType.OPENING_WITHDRAWAL,
                    PaymentMethod.CASH,
                    fundWithdrawalAmount,
                    "Apertura de caja desde fondo acumulado",
                    trimToNull(request.getOpeningNote()),
                    now
            );
        }

        return mapResponse(cashRegister);
    }

    @Override
    @Transactional
    public CashRegisterResponse getCurrent(Long tenantId, Long branchId) {
        autoCloseOpenRegisterIfExpired(tenantId, branchId);

        CashRegister cashRegister = cashRegisterRepository
                .findByTenant_IdAndBranch_IdAndStatus(tenantId, branchId, CashRegisterStatus.OPEN)
                .orElseThrow(() -> new GlobalExceptionHandler.ResourceNotFoundException(
                        "No hay una caja abierta en esta sede."
                ));

        return mapResponse(cashRegister);
    }

    @Override
    @Transactional(readOnly = true)
    public CashRegisterResponse getPendingReconciliation(Long tenantId, Long branchId) {
        CashRegister cashRegister = cashRegisterRepository
                .findFirstByTenant_IdAndBranch_IdAndReconciliationRequiredTrueOrderByOpenedAtDesc(tenantId, branchId)
                .orElseThrow(() -> new GlobalExceptionHandler.ResourceNotFoundException(
                        "No hay cierres pendientes de conciliacion en esta sede."
                ));
        return mapResponse(cashRegister);
    }

    @Override
    public CashRegisterResponse reconcile(Long tenantId, Long branchId, Long cashRegisterId, Long actorUserId, ReconcileCashRegisterRequest request) {
        validateCashActor(actorUserId, tenantId);
        CashRegister cashRegister = getCashRegisterInBranch(tenantId, branchId, cashRegisterId);
        if (!Boolean.TRUE.equals(cashRegister.getReconciliationRequired())) {
            throw new IllegalStateException("Esta caja no tiene una conciliacion pendiente.");
        }
        AppUser actor = appUserRepository.findById(actorUserId)
                .orElseThrow(() -> new EntityNotFoundException("Usuario autenticado no encontrado."));
        CashTotals totals = calculateCashTotals(cashRegister);
        BigDecimal counted = request.getClosingAmountCounted() == null
                ? totals.expectedCash()
                : safe(request.getClosingAmountCounted());
        if (counted.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("El efectivo contado no puede ser negativo.");
        }

        Map<String, BigDecimal> availableByMethod = calculatePaymentMethodBalances(cashRegister, null);
        Map<String, BigDecimal> requestedDeposits = new LinkedHashMap<>();
        if (request.getFundDeposits() != null) {
            for (Map.Entry<PaymentMethod, BigDecimal> entry : request.getFundDeposits().entrySet()) {
                if (entry.getKey() == null) continue;
                BigDecimal amount = safe(entry.getValue());
                if (amount.compareTo(BigDecimal.ZERO) <= 0) continue;
                String code = normalizePaymentMethodCode(entry.getKey().name());
                requestedDeposits.put(code, requestedDeposits.getOrDefault(code, BigDecimal.ZERO).add(amount));
            }
        }
        if (!requestedDeposits.isEmpty()) {
            adminPermissionService.checkOwnerOrAdminPermission("CASH_FUND_MANAGE");
        }
        for (Map.Entry<String, BigDecimal> entry : requestedDeposits.entrySet()) {
            String code = entry.getKey();
            BigDecimal amount = entry.getValue();
            BigDecimal available = "CASH".equals(code)
                    ? counted
                    : availableByMethod.getOrDefault(code, BigDecimal.ZERO);
            if (amount.compareTo(available) > 0) {
                throw new IllegalStateException("El monto enviado al fondo supera el saldo disponible en " + paymentMethodLabel(code) + ".");
            }
            createFundMovementEntity(
                    cashRegister.getTenant(), cashRegister.getBranch(), cashRegister, actor,
                    CashFundMovementType.CLOSING_DEPOSIT, PaymentMethod.valueOf(code), amount,
                    "Saldo conciliado enviado al fondo acumulado", trimToNull(request.getNote()),
                    LocalDateTime.now(getZoneIdForTenant(tenantId))
            );
        }
        cashRegister.setClosingAmountExpected(totals.expectedCash());
        cashRegister.setClosingAmountCounted(counted);
        cashRegister.setDifferenceAmount(counted.subtract(totals.expectedCash()));
        cashRegister.setReconciliationRequired(false);
        cashRegister.setReconciliationNote(trimToNull(request.getNote()));
        cashRegisterRepository.save(cashRegister);
        return mapResponse(cashRegister);
    }
    @Override
    public CashRegisterResponse close(Long tenantId, Long branchId, Long cashRegisterId, CloseCashRegisterRequest request) {
        autoCloseOpenRegisterIfExpired(tenantId, branchId);

        CashRegister cashRegister = getCashRegisterInBranch(tenantId, branchId, cashRegisterId);

        if (cashRegister.getStatus() != CashRegisterStatus.OPEN) {
            throw new IllegalStateException("La caja ya no está abierta.");
        }

        CashTotals totals = calculateCashTotals(cashRegister);
        BigDecimal counted = safe(request.getClosingAmountCounted());
        BigDecimal difference = counted.subtract(totals.expectedCash());

        ZoneId zoneId = getZoneIdForTenant(tenantId);

        cashRegister.setClosingAmountExpected(totals.expectedCash());
        cashRegister.setClosingAmountCounted(counted);
        cashRegister.setDifferenceAmount(difference);
        cashRegister.setClosedAt(LocalDateTime.now(zoneId));
        cashRegister.setClosingNote(request.getClosingNote());
        cashRegister.setStatus(CashRegisterStatus.CLOSED);
        cashRegister.setReconciliationRequired(false);
        cashRegister.setReconciliationNote(null);

        cashRegisterRepository.save(cashRegister);

        BigDecimal fundDepositAmount = safe(request.getFundDepositAmount());
        if (fundDepositAmount.compareTo(BigDecimal.ZERO) > 0) {
            adminPermissionService.checkOwnerOrAdminPermission("CASH_FUND_MANAGE");
            if (fundDepositAmount.compareTo(counted) > 0) {
                throw new IllegalStateException("El monto enviado al fondo no puede superar el efectivo contado al cierre.");
            }
            createFundMovementEntity(
                    cashRegister.getTenant(), cashRegister.getBranch(), cashRegister,
                    cashRegister.getOpenedByUser(),
                    CashFundMovementType.CLOSING_DEPOSIT,
                    PaymentMethod.CASH,
                    fundDepositAmount,
                    "Sobrante enviado a fondo acumulado",
                    trimToNull(request.getClosingNote()),
                    cashRegister.getClosedAt()
            );
        }

        return mapResponse(cashRegister);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CashRegisterResponse> history(Long tenantId, Long branchId, LocalDate from, LocalDate to) {
        LocalDateTime fromDateTime = from.atStartOfDay();
        LocalDateTime toDateTime = to.plusDays(1).atStartOfDay();

        return cashRegisterRepository
                .findHistoryByOpenedAtOrActivityBetween(
                        tenantId, branchId, fromDateTime, toDateTime
                )
                .stream()
                .map(cashRegister -> mapResponse(cashRegister, fromDateTime, toDateTime))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CashAuditLogResponse> audit(Long tenantId, Long branchId, Long cashRegisterId, Long actorUserId, LocalDate from, LocalDate to) {
        LocalDateTime fromDateTime = from.atStartOfDay();
        LocalDateTime toDateTime = to.plusDays(1).atStartOfDay();

        return cashAuditLogRepository
                .findByBranchAndRange(tenantId, branchId, cashRegisterId, actorUserId, fromDateTime, toDateTime)
                .stream()
                .map(this::mapAuditResponse)
                .toList();
    }

    private CashAuditLogResponse mapAuditResponse(CashAuditLog log) {
        AppUser actor = log.getActorUser();
        return CashAuditLogResponse.builder()
                .id(log.getId())
                .cashRegisterId(log.getCashRegister() == null ? null : log.getCashRegister().getId())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .action(log.getAction())
                .reason(log.getReason())
                .beforeSnapshot(log.getBeforeSnapshot())
                .afterSnapshot(log.getAfterSnapshot())
                .createdAt(log.getCreatedAt())
                .actorUserId(actor == null ? null : actor.getId())
                .actorUserName(actor == null ? null : fullName(actor))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CashFundSummaryResponse getFundSummary(Long tenantId, Long branchId) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new EntityNotFoundException("Sede no encontrada"));
        if (!branch.getTenant().getId().equals(tenantId)) {
            throw new IllegalStateException("La sede no pertenece al tenant.");
        }
        return buildFundSummary(branch);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CashFundMovementResponse> getFundMovements(Long tenantId, Long branchId) {
        return cashFundMovementRepository.findByTenant_IdAndBranch_IdOrderByMovementDateDesc(tenantId, branchId)
                .stream()
                .map(this::mapFundMovementResponse)
                .toList();
    }

    @Override
    public CashFundMovementResponse createFundMovement(
            Long tenantId,
            Long branchId,
            Long actorUserId,
            CashFundMovementRequest request
    ) {
        validateCashActor(actorUserId, tenantId);
        adminPermissionService.checkOwnerOrAdminPermission("CASH_FUND_MANAGE");
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant no encontrado"));
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new EntityNotFoundException("Sede no encontrada"));
        if (!branch.getTenant().getId().equals(tenantId)) {
            throw new IllegalStateException("La sede no pertenece al tenant.");
        }
        AppUser actor = appUserRepository.findById(actorUserId)
                .orElseThrow(() -> new EntityNotFoundException("Usuario autenticado no encontrado."));

        CashFundMovementType type = request.getType();
        if (type == null || type == CashFundMovementType.OPENING_WITHDRAWAL || type == CashFundMovementType.CLOSING_DEPOSIT) {
            throw new IllegalStateException("Selecciona un tipo de movimiento de fondo valido.");
        }

        BigDecimal amount = safe(request.getAmount());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("El monto debe ser mayor a cero.");
        }

        PaymentMethod paymentMethod = request.getPaymentMethod() == null ? PaymentMethod.CASH : request.getPaymentMethod();
        if (isFundOut(type)) {
            validateFundAvailable(branch, paymentMethod, amount);
        }

        ZoneId zoneId = getZoneIdForTenant(tenantId);
        LocalDateTime now = LocalDateTime.now(zoneId);
        LocalDateTime movementDate = request.getMovementDate() == null
                ? now
                : request.getMovementDate().atTime(now.toLocalTime());

        CashFundMovement movement = createFundMovementEntity(
                tenant,
                branch,
                null,
                actor,
                type,
                paymentMethod,
                amount,
                resolveFundConcept(type, request.getConcept()),
                trimToNull(request.getNote()),
                movementDate
        );
        return mapFundMovementResponse(movement);
    }
    @Override
    @Transactional(readOnly = true)
    public List<CashMovementResponse> getMovements(Long tenantId, Long branchId, Long cashRegisterId) {
        CashRegister cashRegister = getCashRegisterInBranch(tenantId, branchId, cashRegisterId);
        LocalDateTime[] range = cashRegisterBusinessRange(cashRegister);
        return cashMovementRepository
                .findByTenant_IdAndBranch_IdAndMovementDateGreaterThanEqualAndMovementDateLessThanOrderByMovementDateDesc(
                        tenantId, branchId, range[0], range[1]
                )
                .stream()
                .map(this::mapMovementResponse)
                .toList();
    }

    @Override
    public CashMovementResponse createMovement(
            Long tenantId,
            Long branchId,
            Long cashRegisterId,
            Long actorUserId,
            CashMovementRequest request
    ) {
        ZoneId zoneId = getZoneIdForTenant(tenantId);
        LocalDateTime now = LocalDateTime.now(zoneId);
        LocalDateTime movementDate = resolveMovementDate(request, now, zoneId);

        validateCashActor(actorUserId, tenantId);

        CashRegister cashRegister = getOpenCashRegisterInBranch(tenantId, branchId, cashRegisterId);
        CashRegister movementCashRegister = resolveCashRegisterForMovementDate(
                tenantId, branchId, cashRegister, movementDate
        );

        AppUser actor = appUserRepository.findById(actorUserId)
                .orElseThrow(() -> new EntityNotFoundException("Usuario autenticado no encontrado."));

        BigDecimal amount = safe(request.getAmount());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("El monto debe ser mayor a cero.");
        }

        CashMovementType type = request.getType();
        if (type == null) {
            throw new IllegalStateException("Debes seleccionar un tipo de movimiento.");
        }

        AppUser barberUser = resolveBarberUser(tenantId, branchId, request.getBarberUserId(), type);

        PaymentMethod paymentMethod = resolvePaymentMethod(type, request.getPaymentMethod());
        PaymentMethod fromPaymentMethod = null;
        PaymentMethod toPaymentMethod = null;

        if (type == CashMovementType.PAYMENT_METHOD_TRANSFER) {
            fromPaymentMethod = request.getFromPaymentMethod();
            toPaymentMethod = request.getToPaymentMethod();
            validatePaymentTransfer(fromPaymentMethod, toPaymentMethod);
            validateAvailableBalanceForTransfer(movementCashRegister, fromPaymentMethod, amount, null);
            paymentMethod = toPaymentMethod;
            barberUser = null;
        }

        CashFundingSource fundingSource = request.getFundingSource() == null
                ? CashFundingSource.CASH_REGISTER
                : request.getFundingSource();
        CashFundMovement fundMovement = null;
        if (fundingSource == CashFundingSource.ACCUMULATED_FUND) {
            adminPermissionService.checkOwnerOrAdminPermission("CASH_FUND_MANAGE");
        }
        boolean cashOutflow = type == CashMovementType.EXPENSE
                || type == CashMovementType.ADVANCE_BARBER
                || type == CashMovementType.PAYMENT_BARBER;
        if (cashOutflow && fundingSource == CashFundingSource.CASH_REGISTER) {
            validateAvailableBalanceForTransfer(movementCashRegister, paymentMethod, amount, null);
        }
        if (cashOutflow && fundingSource == CashFundingSource.ACCUMULATED_FUND) {
            validateFundAvailable(movementCashRegister.getBranch(), paymentMethod, amount);
            fundMovement = createFundMovementEntity(
                    movementCashRegister.getTenant(), movementCashRegister.getBranch(), movementCashRegister, actor,
                    CashFundMovementType.EXPENSE, paymentMethod, amount,
                    resolveConcept(type, request.getConcept()), trimToNull(request.getNote()), movementDate
            );
        }
        CashMovement movement = CashMovement.builder()
                .tenant(movementCashRegister.getTenant())
                .branch(movementCashRegister.getBranch())
                .cashRegister(movementCashRegister)
                .user(actor)
                .barberUser(barberUser)
                .type(type)
                .paymentMethod(paymentMethod)
                .fromPaymentMethod(fromPaymentMethod)
                .toPaymentMethod(toPaymentMethod)
                .fundingSource(fundingSource)
                .fundMovement(fundMovement)
                .amount(amount)
                .concept(resolveConcept(type, request.getConcept()))
                .note(trimToNull(request.getNote()))
                .movementDate(movementDate)
                .createdAt(now)
                .build();

        CashMovement savedMovement = cashMovementRepository.save(movement);
        recalculateClosedRegisterAfterMovementChange(movementCashRegister);
        return mapMovementResponse(savedMovement);
    }

    private LocalDateTime resolveMovementDate(
            CashMovementRequest request,
            LocalDateTime now,
            ZoneId zoneId
    ) {
        if (request == null || request.getMovementDate() == null) {
            return now;
        }

        LocalDate requestedDate = request.getMovementDate();
        LocalDate today = LocalDate.now(zoneId);

        if (requestedDate.isAfter(today)) {
            throw new IllegalStateException("No puedes registrar un movimiento con fecha futura.");
        }

        return requestedDate.atTime(now.toLocalTime());
    }

    private CashRegister resolveCashRegisterForMovementDate(
            Long tenantId,
            Long branchId,
            CashRegister fallback,
            LocalDateTime movementDate
    ) {
        if (movementDate == null) {
            return fallback;
        }

        LocalDate day = movementDate.toLocalDate();
        LocalDateTime start = day.atStartOfDay();
        LocalDateTime end = day.plusDays(1).atStartOfDay();

        return cashRegisterRepository
                .findFirstByTenant_IdAndBranch_IdAndOpenedAtGreaterThanEqualAndOpenedAtLessThanOrderByOpenedAtDesc(
                        tenantId, branchId, start, end
                )
                .orElse(fallback);
    }
    private PaymentMethod resolvePaymentMethod(CashMovementType type, PaymentMethod paymentMethod) {
        if (type == CashMovementType.PAYMENT_METHOD_TRANSFER) {
            return null;
        }
        return paymentMethod == null ? PaymentMethod.CASH : paymentMethod;
    }

    private void validatePaymentTransfer(PaymentMethod fromPaymentMethod, PaymentMethod toPaymentMethod) {
        if (fromPaymentMethod == null) {
            throw new IllegalStateException("Selecciona el método de origen.");
        }
        if (toPaymentMethod == null) {
            throw new IllegalStateException("Selecciona el método de destino.");
        }
        if (normalizePaymentMethodCode(fromPaymentMethod.name()).equals(normalizePaymentMethodCode(toPaymentMethod.name()))) {
            throw new IllegalStateException("El origen y destino no pueden ser el mismo método.");
        }
    }

    private void validateAvailableBalanceForTransfer(
            CashRegister cashRegister,
            PaymentMethod fromPaymentMethod,
            BigDecimal amount,
            Long excludedMovementId
    ) {
        String fromCode = normalizePaymentMethodCode(fromPaymentMethod == null ? null : fromPaymentMethod.name());
        BigDecimal available = calculatePaymentMethodBalances(cashRegister, excludedMovementId)
                .getOrDefault(fromCode, BigDecimal.ZERO);

        if (safe(amount).compareTo(available) > 0) {
            throw new IllegalStateException(
                    "No puedes trasladar S/ " + safe(amount).setScale(2, java.math.RoundingMode.HALF_UP)
                            + " desde " + paymentMethodLabel(fromCode)
                            + " porque solo tienes S/ " + available.setScale(2, java.math.RoundingMode.HALF_UP)
                            + " disponible."
            );
        }
    }


    private void validateCashActor(Long actorUserId, Long tenantId) {
        boolean allowed = userTenantRoleRepository.existsByUserIdAndTenantIdAndRoleIn(
                actorUserId,
                tenantId,
                List.of(RoleType.OWNER, RoleType.ADMIN)
        );

        if (!allowed) {
            throw new IllegalStateException(
                    "Solo el dueño o un admin pueden registrar gastos y pagos de caja."
            );
        }
    }

    @Override
    public CashMovementResponse updateMovement(Long tenantId, Long branchId, Long movementId, Long actorUserId, CashMovementRequest request) {
        ZoneId zoneId = getZoneIdForTenant(tenantId);
        LocalDateTime now = LocalDateTime.now(zoneId);

        validateCashActor(actorUserId, tenantId);

        CashMovement movement = cashMovementRepository.findByIdAndTenant_Id(movementId, tenantId)
                .orElseThrow(() -> new IllegalStateException("Movimiento no encontrado."));

        CashRegister cashRegister = movement.getCashRegister();
        validateCashRegisterBranch(branchId, cashRegister);
        BigDecimal amount = safe(request.getAmount());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("El monto debe ser mayor a cero.");
        }

        CashFundingSource currentFundingSource = movement.getFundingSource() == null
                ? CashFundingSource.CASH_REGISTER
                : movement.getFundingSource();
        CashFundingSource requestedFundingSource = request.getFundingSource() == null
                ? currentFundingSource
                : request.getFundingSource();
        if (requestedFundingSource != currentFundingSource) {
            throw new IllegalStateException("Para cambiar el origen del dinero elimina el movimiento y registralo nuevamente.");
        }
        if (currentFundingSource == CashFundingSource.ACCUMULATED_FUND) {
            adminPermissionService.checkOwnerOrAdminPermission("CASH_FUND_MANAGE");
            throw new IllegalStateException("Para corregir un gasto pagado desde fondo, eliminalo y registralo nuevamente.");
        }
        CashMovementType type = request.getType() == null ? movement.getType() : request.getType();
        AppUser barberUser = resolveBarberUser(tenantId, branchId, request.getBarberUserId(), type);
        PaymentMethod paymentMethod = request.getPaymentMethod() == null
                ? (movement.getPaymentMethod() == null ? PaymentMethod.CASH : movement.getPaymentMethod())
                : request.getPaymentMethod();
        PaymentMethod fromPaymentMethod = null;
        PaymentMethod toPaymentMethod = null;

        CashRegister targetRegister = cashRegister;
        LocalDateTime targetMovementDate = movement.getMovementDate();
        if (request.getMovementDate() != null) {
            targetMovementDate = resolveMovementDate(request, now, zoneId);
            targetRegister = resolveCashRegisterForMovementDate(
                    tenantId, branchId, cashRegister, targetMovementDate
            );
        }

        if (type == CashMovementType.PAYMENT_METHOD_TRANSFER) {
            fromPaymentMethod = request.getFromPaymentMethod() == null
                    ? movement.getFromPaymentMethod()
                    : request.getFromPaymentMethod();
            toPaymentMethod = request.getToPaymentMethod() == null
                    ? movement.getToPaymentMethod()
                    : request.getToPaymentMethod();
            validatePaymentTransfer(fromPaymentMethod, toPaymentMethod);
            validateAvailableBalanceForTransfer(targetRegister, fromPaymentMethod, amount, movement.getId());
            paymentMethod = toPaymentMethod;
            barberUser = null;
        }

        boolean outflow = type == CashMovementType.EXPENSE
                || type == CashMovementType.ADVANCE_BARBER
                || type == CashMovementType.PAYMENT_BARBER;
        if (outflow && currentFundingSource == CashFundingSource.CASH_REGISTER) {
            validateAvailableBalanceForTransfer(targetRegister, paymentMethod, amount, movement.getId());
        }

        movement.setType(type);
        movement.setPaymentMethod(paymentMethod);
        movement.setFromPaymentMethod(fromPaymentMethod);
        movement.setToPaymentMethod(toPaymentMethod);
        movement.setAmount(amount);
        movement.setConcept(resolveConcept(type, request.getConcept()));
        movement.setNote(trimToNull(request.getNote()));
        movement.setBarberUser(barberUser);

        if (request.getMovementDate() != null) {
            movement.setCashRegister(targetRegister);
            movement.setTenant(targetRegister.getTenant());
            movement.setBranch(targetRegister.getBranch());
            movement.setMovementDate(targetMovementDate);
        }

        CashRegister previousRegister = cashRegister;
        CashMovement savedMovement = cashMovementRepository.save(movement);
        recalculateClosedRegisterAfterMovementChange(previousRegister);
        if (!previousRegister.getId().equals(savedMovement.getCashRegister().getId())) {
            recalculateClosedRegisterAfterMovementChange(savedMovement.getCashRegister());
        }
        return mapMovementResponse(savedMovement);
    }

    @Override
    public void deleteMovement(Long tenantId, Long branchId, Long movementId, Long actorUserId, String auditReason) {
        validateCashActor(actorUserId, tenantId);

        CashMovement movement = cashMovementRepository.findByIdAndTenant_Id(movementId, tenantId)
                .orElseThrow(() -> new IllegalStateException("Movimiento no encontrado."));

        CashRegister cashRegister = movement.getCashRegister();
        validateCashRegisterBranch(branchId, cashRegister);
        barberPaymentRepository.findByCashMovement_Id(movementId)
                .ifPresent(barberPaymentRepository::delete);

        CashFundMovement linkedFundMovement = movement.getFundMovement();
        if (linkedFundMovement != null || movement.getFundingSource() == CashFundingSource.ACCUMULATED_FUND) {
            adminPermissionService.checkOwnerOrAdminPermission("CASH_FUND_MANAGE");
        }
        cashMovementRepository.delete(movement);
        cashMovementRepository.flush();
        if (linkedFundMovement != null) {
            cashFundMovementRepository.delete(linkedFundMovement);
        }
        recalculateClosedRegisterAfterMovementChange(cashRegister);
    }

    private void recalculateClosedRegisterAfterMovementChange(CashRegister cashRegister) {
        if (cashRegister == null || cashRegister.getStatus() == CashRegisterStatus.OPEN) {
            return;
        }
        CashTotals recalculated = calculateCashTotals(cashRegister);
        BigDecimal expected = recalculated.expectedCash();
        BigDecimal counted = safe(cashRegister.getClosingAmountCounted());
        cashRegister.setClosingAmountExpected(expected);
        cashRegister.setDifferenceAmount(counted.subtract(expected));
        cashRegister.setReconciliationRequired(true);
        cashRegister.setReconciliationNote("Un movimiento posterior modifico esta caja cerrada. Revisa y concilia el saldo.");
        cashRegisterRepository.save(cashRegister);
    }
    private void autoCloseOpenRegisterIfExpired(Long tenantId, Long branchId) {
        CashRegister openRegister = cashRegisterRepository
                .findByTenant_IdAndBranch_IdAndStatus(tenantId, branchId, CashRegisterStatus.OPEN)
                .orElse(null);

        if (openRegister == null) {
            return;
        }

        ZoneId zoneId = getZoneIdForTenant(tenantId);
        LocalDate today = LocalDate.now(zoneId);
        LocalDate openedBusinessDate = openRegister.getOpenedAt().atZone(zoneId).toLocalDate();

        if (!openedBusinessDate.isBefore(today)) {
            return;
        }

        CashTotals totals = calculateCashTotals(openRegister);

        openRegister.setClosingAmountExpected(totals.expectedCash());
        openRegister.setClosingAmountCounted(totals.expectedCash());
        openRegister.setDifferenceAmount(BigDecimal.ZERO);
        openRegister.setClosedAt(LocalDateTime.now(zoneId));
        openRegister.setClosingNote("Cierre automático por cambio de día según zona horaria del tenant.");
        openRegister.setStatus(CashRegisterStatus.AUTO_CLOSED);
        openRegister.setReconciliationRequired(true);
        openRegister.setReconciliationNote("Confirma el efectivo, envia el saldo al fondo o registra el gasto pendiente.");

        cashRegisterRepository.save(openRegister);
    }

    private LocalDateTime[] cashRegisterBusinessRange(CashRegister cashRegister) {
        ZoneId zoneId = getZoneIdForTenant(cashRegister.getTenant().getId());

        LocalDate businessDate = cashRegister.getOpenedAt()
                .atZone(zoneId)
                .toLocalDate();

        LocalDateTime start = businessDate.atStartOfDay();
        LocalDateTime end = businessDate.plusDays(1).atStartOfDay();

        return new LocalDateTime[]{start, end};
    }

    private CashRegisterResponse mapResponse(CashRegister cashRegister) {
        CashTotals totals = calculateCashTotals(cashRegister);
        return mapResponse(cashRegister, totals);
    }

    private CashRegisterResponse mapResponse(CashRegister cashRegister, LocalDateTime start, LocalDateTime end) {
        CashTotals totals = calculateCashTotals(cashRegister, start, end);
        return mapResponse(cashRegister, totals);
    }

    private CashRegisterResponse mapResponse(CashRegister cashRegister, CashTotals totals) {

        BigDecimal closingExpected = cashRegister.getStatus() == CashRegisterStatus.OPEN
                ? totals.expectedCash()
                : safe(cashRegister.getClosingAmountExpected()).compareTo(BigDecimal.ZERO) == 0
                ? totals.expectedCash()
                : safe(cashRegister.getClosingAmountExpected());

        List<CashMovementResponse> movementResponses = totals.movements().stream()
                .map(this::mapMovementResponse)
                .toList();

        List<PaymentMethodSummaryResponse> paymentMethodsSummary =
                buildPaymentMethodsSummary(cashRegister);
        List<PaymentMethodSummaryResponse> paymentMethodBalances =
                buildPaymentMethodBalances(cashRegister);
        CashFundSummaryResponse fundSummary = buildFundSummary(cashRegister.getBranch());

        return CashRegisterResponse.builder()
                .id(cashRegister.getId())
                .status(cashRegister.getStatus().name())
                .branchId(cashRegister.getBranch().getId())
                .branchName(cashRegister.getBranch().getNombre())
                .openedByUserId(cashRegister.getOpenedByUser().getId())
                .openedByUserName(fullName(cashRegister.getOpenedByUser()))
                .assignedUserId(cashRegister.getAssignedUser() != null ? cashRegister.getAssignedUser().getId() : null)
                .assignedUserName(cashRegister.getAssignedUser() != null ? fullName(cashRegister.getAssignedUser()) : null)
                .openingAmount(safe(cashRegister.getOpeningAmount()))
                .closingAmountExpected(closingExpected)
                .closingAmountCounted(safe(cashRegister.getClosingAmountCounted()))
                .differenceAmount(safe(cashRegister.getDifferenceAmount()))
                .openedAt(cashRegister.getOpenedAt())
                .closedAt(cashRegister.getClosedAt())
                .openingNote(cashRegister.getOpeningNote())
                .closingNote(cashRegister.getClosingNote())
                .reconciliationRequired(Boolean.TRUE.equals(cashRegister.getReconciliationRequired()))
                .reconciliationNote(cashRegister.getReconciliationNote())
                .salesTotal(totals.salesTotal())
                .cashSalesTotal(totals.cashSalesTotal())
                .paymentMethodsSummary(paymentMethodsSummary)
                .paymentMethodBalances(paymentMethodBalances)
                .accumulatedFundBalance(fundSummary.getTotalBalance())
                .accumulatedFundBalances(fundSummary.getBalances())
                .movementsIncome(totals.movementsIncome())
                .movementsExpense(totals.movementsExpense())
                .cashMovementsExpense(totals.cashMovementsExpense())
                .movementsExpenseGeneral(totals.movementsExpenseGeneral())
                .movementsAdvanceBarber(totals.movementsAdvanceBarber())
                .movementsPaymentBarber(totals.movementsPaymentBarber())
                .movements(movementResponses)
                .build();
    }

    private CashFundSummaryResponse buildFundSummary(Branch branch) {
        List<CashFundMovement> movements = cashFundMovementRepository
                .findByTenant_IdAndBranch_IdOrderByMovementDateDesc(branch.getTenant().getId(), branch.getId());
        Map<String, BigDecimal> balances = new LinkedHashMap<>();
        for (CashFundMovement movement : movements) {
            String method = normalizePaymentMethodCode(movement.getPaymentMethod());
            BigDecimal signed = signedFundAmount(movement);
            balances.put(method, balances.getOrDefault(method, BigDecimal.ZERO).add(signed));
        }
        List<PaymentMethodSummaryResponse> balanceRows = balances.entrySet().stream()
                .map(entry -> PaymentMethodSummaryResponse.builder()
                        .paymentMethod(entry.getKey())
                        .count(0L)
                        .totalAmount(safe(entry.getValue()))
                        .build())
                .toList();
        BigDecimal total = balances.values().stream()
                .map(this::safe)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return CashFundSummaryResponse.builder()
                .branchId(branch.getId())
                .branchName(branch.getNombre())
                .totalBalance(total)
                .balances(balanceRows)
                .build();
    }

    private CashFundMovement createFundMovementEntity(
            Tenant tenant,
            Branch branch,
            CashRegister cashRegister,
            AppUser actor,
            CashFundMovementType type,
            PaymentMethod paymentMethod,
            BigDecimal amount,
            String concept,
            String note,
            LocalDateTime movementDate
    ) {
        CashFundMovement movement = CashFundMovement.builder()
                .tenant(tenant)
                .branch(branch)
                .cashRegister(cashRegister)
                .actorUser(actor)
                .type(type)
                .paymentMethod(paymentMethod == null ? PaymentMethod.CASH : paymentMethod)
                .amount(safe(amount))
                .concept(resolveFundConcept(type, concept))
                .note(note)
                .movementDate(movementDate)
                .createdAt(LocalDateTime.now(getZoneIdForTenant(tenant.getId())))
                .build();
        return cashFundMovementRepository.save(movement);
    }

    private CashFundMovementResponse mapFundMovementResponse(CashFundMovement movement) {
        AppUser actor = movement.getActorUser();
        return CashFundMovementResponse.builder()
                .id(movement.getId())
                .type(movement.getType().name())
                .paymentMethod(normalizePaymentMethodCode(movement.getPaymentMethod()))
                .amount(safe(movement.getAmount()))
                .signedAmount(signedFundAmount(movement))
                .concept(movement.getConcept())
                .note(movement.getNote())
                .movementDate(movement.getMovementDate())
                .cashRegisterId(movement.getCashRegister() == null ? null : movement.getCashRegister().getId())
                .actorUserId(actor == null ? null : actor.getId())
                .actorUserName(actor == null ? null : fullName(actor))
                .build();
    }

    private void validateFundAvailable(Branch branch, PaymentMethod paymentMethod, BigDecimal amount) {
        CashFundSummaryResponse summary = buildFundSummary(branch);
        String method = normalizePaymentMethodCode(paymentMethod == null ? null : paymentMethod.name());
        BigDecimal available = summary.getBalances().stream()
                .filter(row -> method.equals(normalizePaymentMethodCode(row.getPaymentMethod())))
                .map(PaymentMethodSummaryResponse::getTotalAmount)
                .findFirst()
                .orElse(BigDecimal.ZERO);
        if (safe(amount).compareTo(available) > 0) {
            throw new IllegalStateException(
                    "El fondo acumulado no tiene saldo suficiente en " + paymentMethodLabel(method)
                            + ". Disponible: S/ " + available.setScale(2, java.math.RoundingMode.HALF_UP)
            );
        }
    }

    private BigDecimal signedFundAmount(CashFundMovement movement) {
        if (movement == null || movement.getType() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal amount = safe(movement.getAmount());
        return isFundOut(movement.getType()) ? amount.negate() : amount;
    }

    private boolean isFundOut(CashFundMovementType type) {
        return type == CashFundMovementType.OPENING_WITHDRAWAL
                || type == CashFundMovementType.MANUAL_WITHDRAWAL
                || type == CashFundMovementType.EXPENSE
                || type == CashFundMovementType.ADJUSTMENT_OUT;
    }

    private String resolveFundConcept(CashFundMovementType type, String requestedConcept) {
        String clean = trimToNull(requestedConcept);
        if (clean != null) return clean;
        return switch (type) {
            case CLOSING_DEPOSIT -> "Sobrante de cierre";
            case OPENING_WITHDRAWAL -> "Apertura desde fondo acumulado";
            case MANUAL_DEPOSIT -> "Ingreso a fondo acumulado";
            case MANUAL_WITHDRAWAL -> "Retiro de fondo acumulado";
            case EXPENSE -> "Gasto desde fondo acumulado";
            case ADJUSTMENT_IN -> "Ajuste positivo de fondo";
            case ADJUSTMENT_OUT -> "Ajuste negativo de fondo";
        };
    }
    private CashTotals calculateCashTotals(CashRegister cashRegister) {
        LocalDateTime[] range = cashRegisterBusinessRange(cashRegister);
        return calculateCashTotals(cashRegister, range[0], range[1]);
    }

    private CashTotals calculateCashTotals(CashRegister cashRegister, LocalDateTime start, LocalDateTime end) {

        BigDecimal salesTotal = safe(
                saleRepository.sumTotalByCashRegisterIdAndBusinessDateRange(
                        cashRegister.getId(),
                        start,
                        end
                )
        );

        BigDecimal cashSalesTotal = safe(
                saleRepository.sumCashTotalByCashRegisterIdAndBusinessDateRange(
                        cashRegister.getId(),
                        start,
                        end
                )
        );

        List<CashMovement> movementsInBusinessRange = cashMovementRepository
                .findByTenant_IdAndBranch_IdAndMovementDateGreaterThanEqualAndMovementDateLessThanOrderByMovementDateDesc(
                        cashRegister.getTenant().getId(),
                        cashRegister.getBranch().getId(),
                        start,
                        end
                );

        /*
         * Importante:
         * - Los totales del resumen deben incluir TODOS los métodos de pago
         *   (CASH, YAPE, PLIN, CARD, TRANSFER, etc.).
         * - El efectivo físico esperado debe calcularse SOLO con movimientos que afectan
         *   el cajón físico de efectivo.
         *
         * Antes todo usaba affectsCashDrawer(), por eso gastos/ingresos en Yape, Plin
         * o Tarjeta se veían en el detalle, pero no se reflejaban en el resumen.
         */
        BigDecimal movementsIncome = movementsInBusinessRange.stream()
                .filter(this::isIncomeMovement)
                .map(CashMovement::getAmount)
                .map(this::safe)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal cashMovementsIncome = movementsInBusinessRange.stream()
                .filter(this::isIncomeMovement)
                .filter(this::affectsCashDrawer)
                .map(CashMovement::getAmount)
                .map(this::safe)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal movementsExpenseGeneral = movementsInBusinessRange.stream()
                .filter(m -> m.getType() == CashMovementType.EXPENSE)
                .map(CashMovement::getAmount)
                .map(this::safe)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal cashMovementsExpenseGeneral = movementsInBusinessRange.stream()
                .filter(m -> m.getType() == CashMovementType.EXPENSE)
                .filter(this::affectsCashDrawer)
                .map(CashMovement::getAmount)
                .map(this::safe)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal movementsAdvanceBarber = movementsInBusinessRange.stream()
                .filter(m -> m.getType() == CashMovementType.ADVANCE_BARBER)
                .map(CashMovement::getAmount)
                .map(this::safe)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal cashMovementsAdvanceBarber = movementsInBusinessRange.stream()
                .filter(m -> m.getType() == CashMovementType.ADVANCE_BARBER)
                .filter(this::affectsCashDrawer)
                .map(CashMovement::getAmount)
                .map(this::safe)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal movementsPaymentBarber = movementsInBusinessRange.stream()
                .filter(m -> m.getType() == CashMovementType.PAYMENT_BARBER)
                .map(CashMovement::getAmount)
                .map(this::safe)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal cashMovementsPaymentBarber = movementsInBusinessRange.stream()
                .filter(m -> m.getType() == CashMovementType.PAYMENT_BARBER)
                .filter(this::affectsCashDrawer)
                .map(CashMovement::getAmount)
                .map(this::safe)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal transfersToCash = movementsInBusinessRange.stream()
                .filter(m -> m.getType() == CashMovementType.PAYMENT_METHOD_TRANSFER)
                .filter(m -> m.getToPaymentMethod() == PaymentMethod.CASH || m.getToPaymentMethod() == PaymentMethod.EFECTIVO)
                .map(CashMovement::getAmount)
                .map(this::safe)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal transfersFromCash = movementsInBusinessRange.stream()
                .filter(m -> m.getType() == CashMovementType.PAYMENT_METHOD_TRANSFER)
                .filter(m -> m.getFromPaymentMethod() == PaymentMethod.CASH || m.getFromPaymentMethod() == PaymentMethod.EFECTIVO)
                .map(CashMovement::getAmount)
                .map(this::safe)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal movementsExpense = movementsExpenseGeneral
                .add(movementsAdvanceBarber)
                .add(movementsPaymentBarber);

        BigDecimal cashMovementsExpense = cashMovementsExpenseGeneral
                .add(cashMovementsAdvanceBarber)
                .add(cashMovementsPaymentBarber);

        BigDecimal expectedCash = safe(cashRegister.getOpeningAmount())
                .add(cashSalesTotal)
                .add(cashMovementsIncome)
                .add(transfersToCash)
                .subtract(transfersFromCash)
                .subtract(cashMovementsExpense);

        return new CashTotals(
                salesTotal,
                cashSalesTotal,
                movementsIncome,
                movementsExpense,
                cashMovementsExpense,
                movementsExpenseGeneral,
                movementsAdvanceBarber,
                movementsPaymentBarber,
                expectedCash,
                movementsInBusinessRange
        );
    }

    private String requireAuditReason(String reason) {
        String clean = reason == null ? "" : reason.trim();
        if (clean.isEmpty()) {
            throw new IllegalArgumentException("El motivo de auditoria es obligatorio.");
        }
        return clean.length() > 500 ? clean.substring(0, 500) : clean;
    }

    private void registerCashAudit(
            Tenant tenant,
            Branch branch,
            CashRegister cashRegister,
            Long actorUserId,
            String entityType,
            Long entityId,
            String action,
            String reason,
            String beforeSnapshot,
            String afterSnapshot
    ) {
        AppUser actor = actorUserId == null ? null : appUserRepository.findById(actorUserId).orElse(null);
        cashAuditLogRepository.save(CashAuditLog.builder()
                .tenant(tenant)
                .branch(branch)
                .cashRegister(cashRegister)
                .actorUser(actor)
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .reason(reason)
                .beforeSnapshot(beforeSnapshot)
                .afterSnapshot(afterSnapshot)
                .createdAt(LocalDateTime.now(getZoneIdForTenant(tenant.getId())))
                .build());
    }

    private String movementAuditSnapshot(CashMovement movement) {
        if (movement == null) {
            return null;
        }
        return "{movementId=" + movement.getId()
                + ",cashRegisterId=" + (movement.getCashRegister() == null ? null : movement.getCashRegister().getId())
                + ",type=" + movement.getType()
                + ",paymentMethod=" + movement.getPaymentMethod()
                + ",fromPaymentMethod=" + movement.getFromPaymentMethod()
                + ",toPaymentMethod=" + movement.getToPaymentMethod()
                + ",amount=" + movement.getAmount()
                + ",concept=" + movement.getConcept()
                + ",note=" + movement.getNote()
                + ",barberUserId=" + (movement.getBarberUser() == null ? null : movement.getBarberUser().getId())
                + ",movementDate=" + movement.getMovementDate()
                + "}";
    }
    private boolean isIncomeMovement(CashMovement movement) {
        return movement.getType() == CashMovementType.INCOME || movement.getType() == CashMovementType.ADJUSTMENT;
    }

    private boolean isMovementInRange(CashMovement movement, LocalDateTime start, LocalDateTime end) {
        if (movement == null || movement.getMovementDate() == null) {
            return false;
        }

        LocalDateTime movementDate = movement.getMovementDate();
        return !movementDate.isBefore(start) && movementDate.isBefore(end);
    }

    private boolean affectsCashDrawer(CashMovement movement) {
        boolean outflow = movement.getType() == CashMovementType.EXPENSE
                || movement.getType() == CashMovementType.ADVANCE_BARBER
                || movement.getType() == CashMovementType.PAYMENT_BARBER;
        if (outflow && movement.getFundingSource() != null
                && movement.getFundingSource() != CashFundingSource.CASH_REGISTER) {
            return false;
        }
        return movement.getPaymentMethod() == null
                || movement.getPaymentMethod() == PaymentMethod.CASH
                || movement.getPaymentMethod() == PaymentMethod.EFECTIVO;
    }

    private CashMovementResponse mapMovementResponse(CashMovement movement) {
        return CashMovementResponse.builder()
                .id(movement.getId())
                .type(movement.getType() == null ? null : movement.getType().name())
                .paymentMethod(movement.getPaymentMethod() == null ? null : movement.getPaymentMethod().name())
                .fundingSource(movement.getFundingSource() == null ? "CASH_REGISTER" : movement.getFundingSource().name())
                .fundMovementId(movement.getFundMovement() == null ? null : movement.getFundMovement().getId())
                .fromPaymentMethod(movement.getFromPaymentMethod() == null ? null : movement.getFromPaymentMethod().name())
                .toPaymentMethod(movement.getToPaymentMethod() == null ? null : movement.getToPaymentMethod().name())
                .amount(safe(movement.getAmount()))
                .concept(movement.getConcept())
                .note(movement.getNote())
                .movementDate(movement.getMovementDate())
                .userId(movement.getUser() != null ? movement.getUser().getId() : null)
                .userName(movement.getUser() != null ? fullName(movement.getUser()) : null)
                .barberUserId(movement.getBarberUser() != null ? movement.getBarberUser().getId() : null)
                .barberUserName(movement.getBarberUser() != null ? fullName(movement.getBarberUser()) : null)
                .build();
    }


    private AppUser resolveBarberUser(Long tenantId, Long branchId, Long barberUserId, CashMovementType type) {
        boolean requiresBarber = type == CashMovementType.ADVANCE_BARBER || type == CashMovementType.PAYMENT_BARBER;

        if (barberUserId == null) {
            if (requiresBarber) {
                throw new IllegalStateException("Debes seleccionar un barbero para este movimiento.");
            }
            return null;
        }

        AppUser barber = appUserRepository.findByIdAndTenant_Id(barberUserId, tenantId)
                .orElseThrow(() -> new IllegalStateException("Barbero no encontrado."));

        boolean hasBarberRoleInBranch = userTenantRoleRepository.existsByUser_IdAndTenant_IdAndBranch_IdAndRole(
                barberUserId,
                tenantId,
                branchId,
                RoleType.BARBER
        );
        if (!hasBarberRoleInBranch) {
            throw new IllegalStateException("El usuario seleccionado no es un barbero valido.");
        }

        return barber;
    }

    private CashRegister getCashRegisterInBranch(Long tenantId, Long branchId, Long cashRegisterId) {
        CashRegister cashRegister = cashRegisterRepository.findByIdAndTenant_Id(cashRegisterId, tenantId)
                .orElseThrow(() -> new IllegalStateException("Caja no encontrada."));
        validateCashRegisterBranch(branchId, cashRegister);
        return cashRegister;
    }

    private CashRegister getOpenCashRegisterInBranch(Long tenantId, Long branchId, Long cashRegisterId) {
        CashRegister cashRegister = getCashRegisterInBranch(tenantId, branchId, cashRegisterId);
        if (cashRegister.getStatus() != CashRegisterStatus.OPEN) {
            throw new IllegalStateException("Solo puedes registrar movimientos en una caja abierta.");
        }
        return cashRegister;
    }

    private void validateCashRegisterBranch(Long branchId, CashRegister cashRegister) {
        if (!cashRegister.getBranch().getId().equals(branchId)) {
            throw new IllegalStateException("La caja no pertenece a esta sede.");
        }
    }

    private String resolveConcept(CashMovementType type, String requestedConcept) {
        String cleanConcept = trimToNull(requestedConcept);
        if (cleanConcept != null) {
            return cleanConcept;
        }

        return switch (type) {
            case ADVANCE_BARBER -> "Adelanto a barbero";
            case PAYMENT_BARBER -> "Pago a barbero";
            case PAYMENT_METHOD_TRANSFER -> "Traslado entre métodos";
            case INCOME -> "Ingreso extra";
            case ADJUSTMENT -> "Ajuste de caja";
            case EXPENSE -> "Otros";
        };
    }

    private String fullName(AppUser user) {
        String nombre = user.getNombre() == null ? "" : user.getNombre().trim();
        String apellido = user.getApellido() == null ? "" : user.getApellido().trim();
        String joined = (nombre + " " + apellido).trim();
        return joined.isEmpty() ? "Usuario" : joined;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private record CashTotals(
            BigDecimal salesTotal,
            BigDecimal cashSalesTotal,
            BigDecimal movementsIncome,
            BigDecimal movementsExpense,
            BigDecimal cashMovementsExpense,
            BigDecimal movementsExpenseGeneral,
            BigDecimal movementsAdvanceBarber,
            BigDecimal movementsPaymentBarber,
            BigDecimal expectedCash,
            List<CashMovement> movements
    ) {
    }


    private List<PaymentMethodSummaryResponse> buildPaymentMethodsSummary(CashRegister cashRegister) {
        if (cashRegister == null || cashRegister.getId() == null) {
            return List.of();
        }

        Map<String, BigDecimal> salesTotals = new LinkedHashMap<>();
        Map<String, Long> counts = new LinkedHashMap<>();

        LocalDateTime[] range = cashRegisterBusinessRange(cashRegister);
        List<Object[]> rows = saleRepository.getPaymentMethodsSummaryByCashRegisterIdAndBusinessDateRange(
                cashRegister.getId(),
                range[0],
                range[1]
        );
        if (rows != null) {
            for (Object[] row : rows) {
                String method = normalizePaymentMethodCode(row[0]);
                Long count = toLong(row[1]);
                BigDecimal total = toBigDecimal(row[2]);
                addToPaymentSummary(salesTotals, counts, method, total, count);
            }
        }

        return salesTotals.entrySet().stream()
                .filter(e -> e.getValue().compareTo(BigDecimal.ZERO) > 0)
                .map(e -> PaymentMethodSummaryResponse.builder()
                        .paymentMethod(e.getKey())
                        .count(counts.getOrDefault(e.getKey(), 0L))
                        .totalAmount(e.getValue())
                        .build())
                .toList();
    }

    private List<PaymentMethodSummaryResponse> buildPaymentMethodBalances(CashRegister cashRegister) {
        if (cashRegister == null || cashRegister.getId() == null) {
            return List.of();
        }

        Map<String, BigDecimal> balances = calculatePaymentMethodBalances(cashRegister, null);
        Map<String, Long> counts = calculatePaymentMethodCounts(cashRegister);

        // Asegura que aparezcan los métodos activos configurados por el dueño, aunque estén en S/ 0.00.
        for (String code : configuredPaymentMethodCodes(cashRegister)) {
            if (code == null || code.isBlank() || "FREE".equals(code)) {
                continue;
            }
            balances.putIfAbsent(code, BigDecimal.ZERO);
            counts.putIfAbsent(code, 0L);
        }

        return balances.entrySet().stream()
                .filter(e -> e.getKey() != null && !e.getKey().isBlank())
                .filter(e -> !"FREE".equals(e.getKey()))
                .map(e -> PaymentMethodSummaryResponse.builder()
                        .paymentMethod(e.getKey())
                        .count(counts.getOrDefault(e.getKey(), 0L))
                        .totalAmount(safe(e.getValue()))
                        .build())
                .toList();
    }

    private List<String> configuredPaymentMethodCodes(CashRegister cashRegister) {
        if (cashRegister == null || cashRegister.getTenant() == null) {
            return List.of("CASH", "YAPE", "PLIN", "TRANSFER", "CARD");
        }

        Long tenantId = cashRegister.getTenant().getId();
        Long branchId = cashRegister.getBranch() == null ? null : cashRegister.getBranch().getId();

        List<TenantPaymentMethod> configured = List.of();
        if (branchId != null) {
            configured = tenantPaymentMethodRepository
                    .findByTenant_IdAndBranch_IdAndActiveTrueOrderBySortOrderAscDisplayNameAsc(tenantId, branchId);
        }
        if (configured == null || configured.isEmpty()) {
            configured = tenantPaymentMethodRepository
                    .findByTenant_IdAndActiveTrueOrderBySortOrderAscDisplayNameAsc(tenantId);
        }

        List<String> codes = new ArrayList<>();
        codes.add("CASH");

        if (configured != null) {
            for (TenantPaymentMethod method : configured) {
                String code = normalizePaymentMethodCode(method == null ? null : method.getCode());
                if (code != null && !code.isBlank() && !codes.contains(code)) {
                    codes.add(code);
                }
            }
        }

        // Fallback para tenants que aún no tengan configuración de métodos.
        if (codes.size() == 1) {
            for (String code : List.of("YAPE", "PLIN", "TRANSFER", "CARD")) {
                if (!codes.contains(code)) codes.add(code);
            }
        }

        return codes;
    }

    private Map<String, BigDecimal> calculatePaymentMethodBalances(CashRegister cashRegister, Long excludedMovementId) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        Map<String, Long> ignoredCounts = new LinkedHashMap<>();

        if (cashRegister == null || cashRegister.getId() == null) {
            return totals;
        }

        // Saldo inicial de caja siempre pertenece a efectivo físico.
        addToPaymentSummary(totals, ignoredCounts, "CASH", safe(cashRegister.getOpeningAmount()), 0L);

        LocalDateTime[] range = cashRegisterBusinessRange(cashRegister);
        List<Object[]> rows = saleRepository.getPaymentMethodsSummaryByCashRegisterIdAndBusinessDateRange(
                cashRegister.getId(),
                range[0],
                range[1]
        );
        if (rows != null) {
            for (Object[] row : rows) {
                String method = normalizePaymentMethodCode(row[0]);
                BigDecimal total = toBigDecimal(row[2]);
                addToPaymentSummary(totals, ignoredCounts, method, total, 0L);
            }
        }

        List<CashMovement> movements = cashMovementRepository.findByCashRegister_IdOrderByMovementDateDesc(cashRegister.getId());
        for (CashMovement movement : movements) {
            if (movement == null || movement.getId() == null) {
                continue;
            }
            if (excludedMovementId != null && excludedMovementId.equals(movement.getId())) {
                continue;
            }

            BigDecimal movementAmount = safe(movement.getAmount());
            if (movementAmount.compareTo(BigDecimal.ZERO) <= 0 || movement.getType() == null) {
                continue;
            }

            if (movement.getType() == CashMovementType.PAYMENT_METHOD_TRANSFER) {
                addToPaymentSummary(totals, ignoredCounts, movement.getFromPaymentMethod(), movementAmount.negate(), 0L);
                addToPaymentSummary(totals, ignoredCounts, movement.getToPaymentMethod(), movementAmount, 0L);
                continue;
            }

            if (movement.getFundingSource() == CashFundingSource.ACCUMULATED_FUND || movement.getFundingSource() == CashFundingSource.EXTERNAL) {
                continue;
            }

            if (isIncomeMovement(movement)) {
                addToPaymentSummary(totals, ignoredCounts, movement.getPaymentMethod(), movementAmount, 0L);
                continue;
            }

            if (movement.getType() == CashMovementType.EXPENSE
                    || movement.getType() == CashMovementType.ADVANCE_BARBER
                    || movement.getType() == CashMovementType.PAYMENT_BARBER) {
                addToPaymentSummary(totals, ignoredCounts, movement.getPaymentMethod(), movementAmount.negate(), 0L);
            }
        }

        return totals;
    }

    private Map<String, Long> calculatePaymentMethodCounts(CashRegister cashRegister) {
        Map<String, Long> counts = new LinkedHashMap<>();
        if (cashRegister == null || cashRegister.getId() == null) {
            return counts;
        }

        LocalDateTime[] range = cashRegisterBusinessRange(cashRegister);
        List<Object[]> rows = saleRepository.getPaymentMethodsSummaryByCashRegisterIdAndBusinessDateRange(
                cashRegister.getId(),
                range[0],
                range[1]
        );
        if (rows != null) {
            for (Object[] row : rows) {
                String method = normalizePaymentMethodCode(row[0]);
                Long count = toLong(row[1]);
                if (method != null && !method.isBlank() && count > 0) {
                    counts.put(method, counts.getOrDefault(method, 0L) + count);
                }
            }
        }

        return counts;
    }

    private void addToPaymentSummary(
            Map<String, BigDecimal> totals,
            Map<String, Long> counts,
            PaymentMethod method,
            BigDecimal amount,
            Long count
    ) {
        addToPaymentSummary(totals, counts, method == null ? "CASH" : method.name(), amount, count);
    }

    private void addToPaymentSummary(
            Map<String, BigDecimal> totals,
            Map<String, Long> counts,
            String method,
            BigDecimal amount,
            Long count
    ) {
        String code = normalizePaymentMethodCode(method);
        if (code == null || code.isBlank() || amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        totals.put(code, totals.getOrDefault(code, BigDecimal.ZERO).add(amount));
        if (count != null && count > 0) {
            counts.put(code, counts.getOrDefault(code, 0L) + count);
        }
    }

    private String normalizePaymentMethodCode(Object raw) {
        if (raw == null) return null;

        String value = raw.toString()
                .trim()
                .toUpperCase(Locale.ROOT)
                .replace("Á", "A")
                .replace("É", "E")
                .replace("Í", "I")
                .replace("Ó", "O")
                .replace("Ú", "U");

        return switch (value) {
            case "EFECTIVO" -> "CASH";
            case "TARJETA" -> "CARD";
            case "TRANSFERENCIA" -> "TRANSFER";
            case "GRATIS" -> "FREE";
            default -> value;
        };
    }

    private String paymentMethodLabel(String code) {
        if (code == null || code.isBlank()) {
            return "el método seleccionado";
        }
        return switch (code) {
            case "CASH" -> "Efectivo";
            case "YAPE" -> "Yape";
            case "PLIN" -> "Plin";
            case "TRANSFER" -> "Transferencia";
            case "CARD" -> "Tarjeta";
            case "FREE" -> "Gratis";
            default -> code;
        };
    }

    private Long toLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (Exception e) {
            return 0L;
        }
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bigDecimal) return bigDecimal;
        if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
        try {
            return new BigDecimal(value.toString());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}

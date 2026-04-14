package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.CashMovementRequest;
import com.gods.saas.domain.dto.request.CloseCashRegisterRequest;
import com.gods.saas.domain.dto.request.OpenCashRegisterRequest;
import com.gods.saas.domain.dto.response.CashMovementResponse;
import com.gods.saas.domain.dto.response.CashRegisterResponse;
import com.gods.saas.domain.enums.CashMovementType;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional
public class CashRegisterServiceImpl implements CashRegisterService {

    private static final String DEFAULT_TIMEZONE = "America/Lima";

    private final CashRegisterRepository cashRegisterRepository;
    private final CashMovementRepository cashMovementRepository;
    private final SaleRepository saleRepository;
    private final TenantRepository tenantRepository;
    private final BranchRepository branchRepository;
    private final AppUserRepository appUserRepository;
    private final TenantSettingsRepository tenantSettingsRepository;
    private final UserTenantRoleRepository userTenantRoleRepository;



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

        cashRegisterRepository.save(cashRegister);

        return mapResponse(cashRegister);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CashRegisterResponse> history(Long tenantId, Long branchId, LocalDate from, LocalDate to) {
        LocalDateTime fromDateTime = from.atStartOfDay();
        LocalDateTime toDateTime = to.plusDays(1).atStartOfDay();

        return cashRegisterRepository
                .findByTenant_IdAndBranch_IdAndOpenedAtBetweenOrderByOpenedAtDesc(
                        tenantId, branchId, fromDateTime, toDateTime
                )
                .stream()
                .map(this::mapResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CashMovementResponse> getMovements(Long tenantId, Long branchId, Long cashRegisterId) {
        CashRegister cashRegister = getCashRegisterInBranch(tenantId, branchId, cashRegisterId);
        return cashMovementRepository.findByCashRegister_IdOrderByMovementDateDesc(cashRegister.getId())
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

        validateCashActor(actorUserId, tenantId);

        CashRegister cashRegister = getOpenCashRegisterInBranch(tenantId, branchId, cashRegisterId);

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

        PaymentMethod paymentMethod = request.getPaymentMethod() == null
                ? PaymentMethod.CASH
                : request.getPaymentMethod();

        CashMovement movement = CashMovement.builder()
                .tenant(cashRegister.getTenant())
                .branch(cashRegister.getBranch())
                .cashRegister(cashRegister)
                .user(actor)
                .barberUser(barberUser)
                .type(type)
                .paymentMethod(paymentMethod)
                .amount(amount)
                .concept(resolveConcept(type, request.getConcept()))
                .note(trimToNull(request.getNote()))
                .movementDate(now)
                .createdAt(now)
                .build();

        return mapMovementResponse(cashMovementRepository.save(movement));
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
        validateCashActor(actorUserId, tenantId);

        CashMovement movement = cashMovementRepository.findByIdAndTenant_Id(movementId, tenantId)
                .orElseThrow(() -> new IllegalStateException("Movimiento no encontrado."));

        CashRegister cashRegister = movement.getCashRegister();
        validateCashRegisterBranch(branchId, cashRegister);

        if (cashRegister.getStatus() != CashRegisterStatus.OPEN) {
            throw new IllegalStateException("Solo puedes editar movimientos de una caja abierta.");
        }

        BigDecimal amount = safe(request.getAmount());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("El monto debe ser mayor a cero.");
        }

        CashMovementType type = request.getType() == null ? movement.getType() : request.getType();
        AppUser barberUser = resolveBarberUser(tenantId, branchId, request.getBarberUserId(), type);
        PaymentMethod paymentMethod = request.getPaymentMethod() == null
                ? (movement.getPaymentMethod() == null ? PaymentMethod.CASH : movement.getPaymentMethod())
                : request.getPaymentMethod();

        movement.setType(type);
        movement.setPaymentMethod(paymentMethod);
        movement.setAmount(amount);
        movement.setConcept(resolveConcept(type, request.getConcept()));
        movement.setNote(trimToNull(request.getNote()));
        movement.setBarberUser(barberUser);

        return mapMovementResponse(cashMovementRepository.save(movement));
    }

    @Override
    public void deleteMovement(Long tenantId, Long branchId, Long movementId, Long actorUserId) {
        validateCashActor(actorUserId, tenantId);

        CashMovement movement = cashMovementRepository.findByIdAndTenant_Id(movementId, tenantId)
                .orElseThrow(() -> new IllegalStateException("Movimiento no encontrado."));

        CashRegister cashRegister = movement.getCashRegister();
        validateCashRegisterBranch(branchId, cashRegister);

        if (cashRegister.getStatus() != CashRegisterStatus.OPEN) {
            throw new IllegalStateException("Solo puedes eliminar movimientos de una caja abierta.");
        }

        cashMovementRepository.delete(movement);
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

        cashRegisterRepository.save(openRegister);
    }

    private CashRegisterResponse mapResponse(CashRegister cashRegister) {
        CashTotals totals = calculateCashTotals(cashRegister);

        BigDecimal closingExpected = cashRegister.getStatus() == CashRegisterStatus.OPEN
                ? totals.expectedCash()
                : safe(cashRegister.getClosingAmountExpected()).compareTo(BigDecimal.ZERO) == 0
                ? totals.expectedCash()
                : safe(cashRegister.getClosingAmountExpected());

        List<CashMovementResponse> movementResponses = totals.movements().stream()
                .map(this::mapMovementResponse)
                .toList();

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
                .salesTotal(totals.salesTotal())
                .cashSalesTotal(totals.cashSalesTotal())
                .movementsIncome(totals.movementsIncome())
                .movementsExpense(totals.movementsExpense())
                .movementsExpenseGeneral(totals.movementsExpenseGeneral())
                .movementsAdvanceBarber(totals.movementsAdvanceBarber())
                .movementsPaymentBarber(totals.movementsPaymentBarber())
                .movements(movementResponses)
                .build();
    }

    private CashTotals calculateCashTotals(CashRegister cashRegister) {
        BigDecimal salesTotal = safe(saleRepository.sumTotalByCashRegisterId(cashRegister.getId()));
        BigDecimal cashSalesTotal = safe(saleRepository.sumCashTotalByCashRegisterId(cashRegister.getId()));
        List<CashMovement> movements = cashMovementRepository.findByCashRegister_IdOrderByMovementDateDesc(cashRegister.getId());

        BigDecimal movementsIncome = movements.stream()
                .filter(this::isIncomeMovement)
                .filter(this::affectsCashDrawer)
                .map(CashMovement::getAmount)
                .map(this::safe)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal movementsExpenseGeneral = movements.stream()
                .filter(m -> m.getType() == CashMovementType.EXPENSE)
                .filter(this::affectsCashDrawer)
                .map(CashMovement::getAmount)
                .map(this::safe)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal movementsAdvanceBarber = movements.stream()
                .filter(m -> m.getType() == CashMovementType.ADVANCE_BARBER)
                .filter(this::affectsCashDrawer)
                .map(CashMovement::getAmount)
                .map(this::safe)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal movementsPaymentBarber = movements.stream()
                .filter(m -> m.getType() == CashMovementType.PAYMENT_BARBER)
                .filter(this::affectsCashDrawer)
                .map(CashMovement::getAmount)
                .map(this::safe)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal movementsExpense = movementsExpenseGeneral
                .add(movementsAdvanceBarber)
                .add(movementsPaymentBarber);

        BigDecimal expectedCash = safe(cashRegister.getOpeningAmount())
                .add(cashSalesTotal)
                .add(movementsIncome)
                .subtract(movementsExpense);

        return new CashTotals(
                salesTotal,
                cashSalesTotal,
                movementsIncome,
                movementsExpense,
                movementsExpenseGeneral,
                movementsAdvanceBarber,
                movementsPaymentBarber,
                expectedCash,
                movements
        );
    }

    private boolean isIncomeMovement(CashMovement movement) {
        return movement.getType() == CashMovementType.INCOME || movement.getType() == CashMovementType.ADJUSTMENT;
    }

    private boolean affectsCashDrawer(CashMovement movement) {
        return movement.getPaymentMethod() == null || movement.getPaymentMethod() == PaymentMethod.CASH;
    }

    private CashMovementResponse mapMovementResponse(CashMovement movement) {
        return CashMovementResponse.builder()
                .id(movement.getId())
                .type(movement.getType() == null ? null : movement.getType().name())
                .paymentMethod(movement.getPaymentMethod() == null ? null : movement.getPaymentMethod().name())
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

        String role = barber.getRol() == null ? "" : barber.getRol().trim().toUpperCase(Locale.ROOT);
        if (!"BARBER".equals(role)) {
            throw new IllegalStateException("El usuario seleccionado no es un barbero válido.");
        }

        if (barber.getBranch() != null && !barber.getBranch().getId().equals(branchId)) {
            throw new IllegalStateException("El barbero no pertenece a esta sede.");
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
            BigDecimal movementsExpenseGeneral,
            BigDecimal movementsAdvanceBarber,
            BigDecimal movementsPaymentBarber,
            BigDecimal expectedCash,
            List<CashMovement> movements
    ) {
    }
}

package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.CloseCashRegisterRequest;
import com.gods.saas.domain.dto.request.OpenCashRegisterRequest;
import com.gods.saas.domain.dto.response.CashRegisterResponse;
import com.gods.saas.domain.enums.CashMovementType;
import com.gods.saas.domain.enums.CashRegisterStatus;
import com.gods.saas.domain.model.AppUser;
import com.gods.saas.domain.model.Branch;
import com.gods.saas.domain.model.CashMovement;
import com.gods.saas.domain.model.CashRegister;
import com.gods.saas.domain.model.Tenant;
import com.gods.saas.domain.model.TenantSettings;
import com.gods.saas.domain.repository.AppUserRepository;
import com.gods.saas.domain.repository.BranchRepository;
import com.gods.saas.domain.repository.CashMovementRepository;
import com.gods.saas.domain.repository.CashRegisterRepository;
import com.gods.saas.domain.repository.SaleRepository;
import com.gods.saas.domain.repository.TenantRepository;
import com.gods.saas.domain.repository.TenantSettingsRepository;
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
    @Transactional(readOnly = true)
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
        System.out.println("CLOSE CASH => tenantId=" + tenantId + " branchId=" + branchId);
        CashRegister cashRegister = cashRegisterRepository
                .findByIdAndTenant_Id(cashRegisterId, tenantId)
                .orElseThrow(() -> new IllegalStateException("Caja no encontrada"));

        if (!cashRegister.getStatus().equals(CashRegisterStatus.OPEN)) {
            throw new IllegalStateException("La caja ya no está abierta.");
        }

        if (!cashRegister.getBranch().getId().equals(branchId)) {
            throw new IllegalStateException("La caja no pertenece a esta sede.");
        }

        BigDecimal openingAmount = safe(cashRegister.getOpeningAmount());
        BigDecimal salesCash = safe(saleRepository.sumCashTotalByCashRegisterId(cashRegister.getId()));

        List<CashMovement> movements = cashMovementRepository
                .findByCashRegister_IdOrderByMovementDateDesc(cashRegister.getId());

        BigDecimal incomes = movements.stream()
                .filter(m -> m.getType() == CashMovementType.INCOME || m.getType() == CashMovementType.ADJUSTMENT)
                .map(CashMovement::getAmount)
                .map(this::safe)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal expenses = movements.stream()
                .filter(m ->
                        m.getType() == CashMovementType.EXPENSE ||
                                m.getType() == CashMovementType.ADVANCE_BARBER ||
                                m.getType() == CashMovementType.PAYMENT_BARBER
                )
                .map(CashMovement::getAmount)
                .map(this::safe)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal expected = openingAmount
                .add(salesCash)
                .add(incomes)
                .subtract(expenses);

        BigDecimal counted = safe(request.getClosingAmountCounted());
        BigDecimal difference = counted.subtract(expected);

        ZoneId zoneId = getZoneIdForTenant(tenantId);

        cashRegister.setClosingAmountExpected(expected);
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
        ZoneId zoneId = getZoneIdForTenant(tenantId);

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

        BigDecimal openingAmount = safe(openRegister.getOpeningAmount());
        BigDecimal salesCash = safe(saleRepository.sumCashTotalByCashRegisterId(openRegister.getId()));

        List<CashMovement> movements = cashMovementRepository
                .findByCashRegister_IdOrderByMovementDateDesc(openRegister.getId());

        BigDecimal incomes = movements.stream()
                .filter(m -> m.getType() == CashMovementType.INCOME || m.getType() == CashMovementType.ADJUSTMENT)
                .map(CashMovement::getAmount)
                .map(this::safe)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal expenses = movements.stream()
                .filter(m ->
                        m.getType() == CashMovementType.EXPENSE ||
                                m.getType() == CashMovementType.ADVANCE_BARBER ||
                                m.getType() == CashMovementType.PAYMENT_BARBER
                )
                .map(CashMovement::getAmount)
                .map(this::safe)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal expected = openingAmount
                .add(salesCash)
                .add(incomes)
                .subtract(expenses);

        openRegister.setClosingAmountExpected(expected);
        openRegister.setClosingAmountCounted(expected);
        openRegister.setDifferenceAmount(BigDecimal.ZERO);
        openRegister.setClosedAt(LocalDateTime.now(zoneId));
        openRegister.setClosingNote("Cierre automático por cambio de día según zona horaria del tenant.");
        openRegister.setStatus(CashRegisterStatus.AUTO_CLOSED);

        cashRegisterRepository.save(openRegister);
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

    private CashRegisterResponse mapResponse(CashRegister cashRegister) {
        BigDecimal salesTotal = safe(saleRepository.sumTotalByCashRegisterId(cashRegister.getId()));
        BigDecimal cashSalesTotal = safe(saleRepository.sumCashTotalByCashRegisterId(cashRegister.getId()));

        List<CashMovement> movements = cashMovementRepository
                .findByCashRegister_IdOrderByMovementDateDesc(cashRegister.getId());

        BigDecimal movementsIncome = movements.stream()
                .filter(m -> m.getType() == CashMovementType.INCOME || m.getType() == CashMovementType.ADJUSTMENT)
                .map(CashMovement::getAmount)
                .map(this::safe)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal movementsExpense = movements.stream()
                .filter(m ->
                        m.getType() == CashMovementType.EXPENSE ||
                                m.getType() == CashMovementType.ADVANCE_BARBER ||
                                m.getType() == CashMovementType.PAYMENT_BARBER
                )
                .map(CashMovement::getAmount)
                .map(this::safe)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CashRegisterResponse.builder()
                .id(cashRegister.getId())
                .status(cashRegister.getStatus().name())
                .branchId(cashRegister.getBranch().getId())
                .branchName(cashRegister.getBranch().getNombre())
                .openedByUserId(cashRegister.getOpenedByUser().getId())
                .openedByUserName(cashRegister.getOpenedByUser().getNombre())
                .assignedUserId(cashRegister.getAssignedUser() != null ? cashRegister.getAssignedUser().getId() : null)
                .assignedUserName(cashRegister.getAssignedUser() != null ? cashRegister.getAssignedUser().getNombre() : null)
                .openingAmount(safe(cashRegister.getOpeningAmount()))
                .closingAmountExpected(safe(cashRegister.getClosingAmountExpected()))
                .closingAmountCounted(safe(cashRegister.getClosingAmountCounted()))
                .differenceAmount(safe(cashRegister.getDifferenceAmount()))
                .openedAt(cashRegister.getOpenedAt())
                .closedAt(cashRegister.getClosedAt())
                .openingNote(cashRegister.getOpeningNote())
                .closingNote(cashRegister.getClosingNote())
                .salesTotal(salesTotal)
                .cashSalesTotal(cashSalesTotal)
                .movementsIncome(movementsIncome)
                .movementsExpense(movementsExpense)
                .build();
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
package com.gods.saas.service.impl.impl;

import com.gods.saas.domain.dto.request.CashMovementRequest;
import com.gods.saas.domain.dto.request.CashFundMovementRequest;
import com.gods.saas.domain.dto.request.CloseCashRegisterRequest;
import com.gods.saas.domain.dto.request.OpenCashRegisterRequest;
import com.gods.saas.domain.dto.request.ReconcileCashRegisterRequest;
import com.gods.saas.domain.dto.response.CashMovementResponse;
import com.gods.saas.domain.dto.response.CashFundMovementResponse;
import com.gods.saas.domain.dto.response.CashFundSummaryResponse;
import com.gods.saas.domain.dto.response.CashRegisterResponse;
import com.gods.saas.domain.dto.response.CashAuditLogResponse;

import java.time.LocalDate;
import java.util.List;

public interface CashRegisterService {

    CashRegisterResponse open(Long tenantId, Long branchId, Long openedByUserId, OpenCashRegisterRequest request);

    CashRegisterResponse getCurrent(Long tenantId, Long branchId);

    CashRegisterResponse getPendingReconciliation(Long tenantId, Long branchId);

    CashRegisterResponse reconcile(Long tenantId, Long branchId, Long cashRegisterId, Long actorUserId, ReconcileCashRegisterRequest request);

    CashRegisterResponse close(Long tenantId, Long branchId, Long cashRegisterId, CloseCashRegisterRequest request);

    List<CashRegisterResponse> history(Long tenantId, Long branchId, LocalDate from, LocalDate to);

    List<CashAuditLogResponse> audit(Long tenantId, Long branchId, Long cashRegisterId, Long actorUserId, LocalDate from, LocalDate to);




    CashFundSummaryResponse getFundSummary(Long tenantId, Long branchId);

    List<CashFundMovementResponse> getFundMovements(Long tenantId, Long branchId);

    CashFundMovementResponse createFundMovement(Long tenantId, Long branchId, Long actorUserId, CashFundMovementRequest request);

    List<CashMovementResponse> getMovements(Long tenantId, Long branchId, Long cashRegisterId);

    CashMovementResponse createMovement(Long tenantId, Long branchId, Long cashRegisterId, Long actorUserId, CashMovementRequest request);

    CashMovementResponse updateMovement(Long tenantId, Long branchId, Long movementId, Long actorUserId, CashMovementRequest request);

    void deleteMovement(Long tenantId, Long branchId, Long movementId, Long actorUserId, String auditReason);
}
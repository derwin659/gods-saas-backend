package com.gods.saas.domain.repository;

import com.gods.saas.domain.enums.CashRegisterStatus;
import com.gods.saas.domain.model.CashRegister;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CashRegisterRepository extends JpaRepository<CashRegister, Long> {

    Optional<CashRegister> findByTenant_IdAndBranch_IdAndStatus(Long tenantId, Long branchId, CashRegisterStatus status);

    boolean existsByTenant_IdAndBranch_IdAndStatus(Long tenantId, Long branchId, CashRegisterStatus status);

    Optional<CashRegister> findByIdAndTenant_Id(Long id, Long tenantId);

    List<CashRegister> findByTenant_IdAndBranch_IdAndOpenedAtBetweenOrderByOpenedAtDesc(
            Long tenantId,
            Long branchId,
            LocalDateTime from,
            LocalDateTime to
    );










}
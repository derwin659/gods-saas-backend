package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.CashMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CashMovementRepository extends JpaRepository<CashMovement, Long> {

    List<CashMovement> findByCashRegister_IdOrderByMovementDateDesc(Long cashRegisterId);


    Optional<CashMovement> findByIdAndTenant_Id(Long movementId, Long tenantId);
}

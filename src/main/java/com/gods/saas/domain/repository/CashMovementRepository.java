package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.CashMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CashMovementRepository extends JpaRepository<CashMovement, Long> {

    List<CashMovement> findByCashRegister_IdOrderByMovementDateDesc(Long cashRegisterId);
}

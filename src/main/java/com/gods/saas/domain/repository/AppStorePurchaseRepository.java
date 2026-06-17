package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.AppStorePurchase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AppStorePurchaseRepository extends JpaRepository<AppStorePurchase, Long> {
    Optional<AppStorePurchase> findTopByOriginalTransactionIdOrderByIdDesc(String originalTransactionId);

    Optional<AppStorePurchase> findTopByTransactionIdOrderByIdDesc(String transactionId);
}

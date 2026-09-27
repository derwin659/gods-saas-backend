package com.gods.saas.service.impl;

import com.gods.saas.domain.repository.LoyaltyPointLotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoyaltyLotConsumptionService {
    private final LoyaltyPointLotRepository repository;

    @Transactional
    public void consume(Long tenantId, Long customerId, int points) {
        if (points <= 0) return;
        for (var lot : repository.findByTenantIdAndCustomerIdAndStatusOrderByExpiresAtAsc(tenantId, customerId, "ACTIVE")) {
            int available = lot.getPointsAvailable() == null ? 0 : Math.max(0, lot.getPointsAvailable());
            int used = Math.min(available, points);
            if (used == 0) continue;
            lot.setPointsAvailable(available - used);
            repository.save(lot);
            points -= used;
            if (points == 0) break;
        }
        // Bonuses and manual credits have no sale lot; their balance is handled by the caller.
    }
}

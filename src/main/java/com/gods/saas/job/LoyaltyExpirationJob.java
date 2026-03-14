package com.gods.saas.job;

import com.gods.saas.service.impl.impl.LoyaltyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@EnableScheduling
@Slf4j
public class LoyaltyExpirationJob {

    private final LoyaltyService loyaltyService;

    @Scheduled(cron = "0 10 2 * * *")
    public void expirePointsDaily() {
        int total = loyaltyService.expirePoints();
        log.info("Puntos expirados procesados: {}", total);
    }
}

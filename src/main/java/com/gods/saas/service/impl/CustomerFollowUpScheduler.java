package com.gods.saas.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomerFollowUpScheduler {

    private final CustomerFollowUpAutomationService customerFollowUpAutomationService;

    @Scheduled(fixedDelay = 300000)
    public void processDueFollowUps() {
        int processed = customerFollowUpAutomationService.processDueFollowUps();
        if (processed > 0) {
            log.info("CUSTOMER FOLLOW-UP SCHEDULER => processed={}", processed);
        }
    }
}
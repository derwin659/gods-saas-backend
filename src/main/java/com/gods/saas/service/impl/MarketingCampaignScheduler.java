package com.gods.saas.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MarketingCampaignScheduler {

    private final MarketingCampaignProcessorService marketingCampaignProcessorService;

    @Scheduled(cron = "0 0 10 * * *")
    public void processAutomaticCampaigns() {
        log.info("MARKETING CAMPAIGN SCHEDULER START");
        marketingCampaignProcessorService.processAllEligibleTenants();
        log.info("MARKETING CAMPAIGN SCHEDULER END");
    }
}
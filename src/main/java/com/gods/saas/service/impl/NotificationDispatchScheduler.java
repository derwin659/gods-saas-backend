package com.gods.saas.service.impl;

import com.gods.saas.service.impl.impl.NotificationDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatchScheduler {

    private final NotificationDispatchService notificationDispatchService;

    @Scheduled(fixedDelay = 60000)
    public void dispatchPendingNotifications() {
        log.info("NOTIFICATION DISPATCH SCHEDULER START");

        notificationDispatchService.processPendingPush();
        notificationDispatchService.processPendingWhatsapp();

        log.info("NOTIFICATION DISPATCH SCHEDULER END");
    }
}
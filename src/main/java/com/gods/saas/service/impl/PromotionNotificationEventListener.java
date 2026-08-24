package com.gods.saas.service.impl;

import com.gods.saas.domain.model.Promotion;
import com.gods.saas.domain.repository.PromotionRepository;
import com.gods.saas.service.impl.impl.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class PromotionNotificationEventListener {

    private final PromotionRepository promotionRepository;
    private final NotificationService notificationService;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(PromotionNotificationRequestedEvent event) {
        if (event == null || event.promotionId() == null) return;

        try {
            Promotion promotion = promotionRepository.findById(event.promotionId())
                    .orElse(null);
            if (promotion == null) {
                log.warn("PROMOTION NOTIFICATION SKIPPED => promotionId={} not found", event.promotionId());
                return;
            }

            notificationService.notifyPromotionCreated(promotion, true);
        } catch (Exception ex) {
            log.error("PROMOTION NOTIFICATION FAILED => promotionId={}", event.promotionId(), ex);
        }
    }
}
package com.gods.saas.service.impl;

import com.gods.saas.domain.enums.NotificationChannel;
import com.gods.saas.domain.enums.NotificationDeliveryStatus;
import com.gods.saas.domain.model.NotificationDelivery;
import com.gods.saas.domain.repository.NotificationDeliveryRepository;
import com.gods.saas.service.impl.impl.NotificationDispatchService;
import com.gods.saas.service.impl.impl.PushNotificationSender;
import com.gods.saas.service.impl.impl.WhatsappNotificationSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class NotificationDispatchServiceImpl implements NotificationDispatchService {

    private final NotificationDeliveryRepository notificationDeliveryRepository;
    private final PushNotificationSender pushNotificationSender;
    private final WhatsappNotificationSender whatsappNotificationSender;

    @Override
    public void processPendingPush() {
        List<NotificationDelivery> pending = notificationDeliveryRepository
                .findTop100ByChannelAndStatusOrderByCreatedAtAsc(
                        NotificationChannel.PUSH,
                        NotificationDeliveryStatus.PENDING
                );

        if (pending.isEmpty()) {
            return;
        }

        log.info("DISPATCH PUSH START => pending={}", pending.size());

        for (NotificationDelivery delivery : pending) {
            processSinglePush(delivery);
        }

        log.info("DISPATCH PUSH END");
    }

    @Override
    public void processPendingWhatsapp() {
        List<NotificationDelivery> pending = notificationDeliveryRepository
                .findTop100ByChannelAndStatusOrderByCreatedAtAsc(
                        NotificationChannel.WHATSAPP,
                        NotificationDeliveryStatus.PENDING
                );

        if (pending.isEmpty()) {
            return;
        }

        log.info("DISPATCH WHATSAPP START => pending={}", pending.size());

        for (NotificationDelivery delivery : pending) {
            processSingleWhatsapp(delivery);
        }

        log.info("DISPATCH WHATSAPP END");
    }

    private void processSinglePush(NotificationDelivery delivery) {
        try {
            String externalId = pushNotificationSender.send(delivery.getNotification());

            delivery.setStatus(NotificationDeliveryStatus.SENT);
            delivery.setSentAt(LocalDateTime.now());
            delivery.setExternalMessageId(externalId);
            delivery.setErrorMessage(null);
        } catch (Exception e) {
            markAsFailed(delivery, e);
        }

        notificationDeliveryRepository.save(delivery);
    }

    private void processSingleWhatsapp(NotificationDelivery delivery) {
        try {
            String externalId = whatsappNotificationSender.send(delivery.getNotification());

            delivery.setStatus(NotificationDeliveryStatus.SENT);
            delivery.setSentAt(LocalDateTime.now());
            delivery.setExternalMessageId(externalId);
            delivery.setErrorMessage(null);
        } catch (Exception e) {
            markAsFailed(delivery, e);
        }

        notificationDeliveryRepository.save(delivery);
    }

    private void markAsFailed(NotificationDelivery delivery, Exception e) {
        int attempts = delivery.getAttempts() == null ? 0 : delivery.getAttempts();

        delivery.setStatus(NotificationDeliveryStatus.FAILED);
        delivery.setAttempts(attempts + 1);
        delivery.setErrorMessage(limit(e.getMessage(), 500));

        log.error(
                "DISPATCH FAILED => deliveryId={}, channel={}, notificationId={}, error={}",
                delivery.getId(),
                delivery.getChannel(),
                delivery.getNotification() != null ? delivery.getNotification().getId() : null,
                e.getMessage(),
                e
        );
    }

    private String limit(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
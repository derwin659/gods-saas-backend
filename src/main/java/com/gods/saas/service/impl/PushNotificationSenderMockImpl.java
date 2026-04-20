package com.gods.saas.service.impl;

import com.gods.saas.domain.model.Notification;
import com.gods.saas.service.impl.impl.PushNotificationSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
public class PushNotificationSenderMockImpl implements PushNotificationSender {

    @Override
    public String send(Notification notification) {
        if (notification == null) {
            throw new IllegalArgumentException("La notificación no puede ser null");
        }

        log.info(
                "MOCK PUSH SENT => notificationId={}, type={}, title={}, customerId={}, userId={}",
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getCustomer() != null ? notification.getCustomer().getId() : null,
                notification.getUser() != null ? notification.getUser().getId() : null
        );

        return "PUSH-MOCK-" + UUID.randomUUID();
    }
}
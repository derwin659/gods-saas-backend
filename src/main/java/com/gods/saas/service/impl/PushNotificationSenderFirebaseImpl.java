package com.gods.saas.service.impl;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.gods.saas.domain.model.DeviceToken;
import com.gods.saas.domain.repository.DeviceTokenRepository;
import com.gods.saas.service.impl.impl.PushNotificationSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PushNotificationSenderFirebaseImpl implements PushNotificationSender {

    private final DeviceTokenRepository deviceTokenRepository;

    @Override
    public String send(com.gods.saas.domain.model.Notification notification) {
        if (notification == null) {
            throw new IllegalArgumentException("La notificación no puede ser null");
        }

        List<DeviceToken> tokens = resolveActiveTokens(notification);

        if (tokens.isEmpty()) {
            throw new RuntimeException("No se encontraron tokens activos para enviar push");
        }

        List<String> successIds = new ArrayList<>();

        for (DeviceToken deviceToken : tokens) {
            try {
                Message message = Message.builder()
                        .setToken(deviceToken.getToken())
                        .setNotification(Notification.builder()
                                .setTitle(notification.getTitle())
                                .setBody(notification.getMessage())
                                .build())
                        .putData("notificationId", String.valueOf(notification.getId()))
                        .putData("type", notification.getType() != null ? notification.getType().name() : "")
                        .putData("referenceType", notification.getReferenceType() != null ? notification.getReferenceType() : "")
                        .putData("referenceId", notification.getReferenceId() != null ? String.valueOf(notification.getReferenceId()) : "")
                        .build();

                String firebaseMessageId = FirebaseMessaging.getInstance().send(message);
                successIds.add(firebaseMessageId);

                log.info(
                        "FIREBASE PUSH SENT => notificationId={}, deviceTokenId={}, firebaseMessageId={}",
                        notification.getId(),
                        deviceToken.getId(),
                        firebaseMessageId
                );
            } catch (Exception e) {
                log.error(
                        "FIREBASE PUSH FAILED => notificationId={}, deviceTokenId={}, error={}",
                        notification.getId(),
                        deviceToken.getId(),
                        e.getMessage(),
                        e
                );

                if (shouldDeactivateToken(e)) {
                    deviceToken.setActive(false);
                    deviceTokenRepository.save(deviceToken);
                    log.warn("FCM token deactivated => deviceTokenId={}", deviceToken.getId());
                }
            }
        }

        if (successIds.isEmpty()) {
            throw new RuntimeException("No se pudo enviar push a ningún token activo");
        }

        return successIds.get(0);
    }

    private List<DeviceToken> resolveActiveTokens(com.gods.saas.domain.model.Notification notification) {
        Long tenantId = notification.getTenant().getId();

        if (notification.getCustomer() != null) {
            return deviceTokenRepository.findByTenant_IdAndCustomer_IdAndActiveTrue(
                    tenantId,
                    notification.getCustomer().getId()
            );
        }

        if (notification.getUser() != null) {
            return deviceTokenRepository.findByTenant_IdAndUser_IdAndActiveTrue(
                    tenantId,
                    notification.getUser().getId()
            );
        }

        return List.of();
    }

    private boolean shouldDeactivateToken(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return false;

        String text = msg.toLowerCase();
        return text.contains("registration-token-not-registered")
                || text.contains("requested entity was not found")
                || text.contains("invalid registration token");
    }
}
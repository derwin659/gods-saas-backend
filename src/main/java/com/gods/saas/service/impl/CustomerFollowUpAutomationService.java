package com.gods.saas.service.impl;

import com.gods.saas.domain.enums.NotificationChannel;
import com.gods.saas.domain.enums.NotificationDeliveryStatus;
import com.gods.saas.domain.enums.NotificationType;
import com.gods.saas.domain.model.Customer;
import com.gods.saas.domain.model.CustomerFollowUp;
import com.gods.saas.domain.model.Notification;
import com.gods.saas.domain.model.NotificationDelivery;
import com.gods.saas.domain.repository.CustomerFollowUpRepository;
import com.gods.saas.domain.repository.NotificationDeliveryRepository;
import com.gods.saas.domain.repository.NotificationRepository;
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
public class CustomerFollowUpAutomationService {

    private static final List<String> AUTOMATIC_CHANNELS = List.of("WHATSAPP", "PUSH", "BOTH", "WHATSAPP_PUSH");

    private final CustomerFollowUpRepository followUpRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryRepository notificationDeliveryRepository;

    public int processDueFollowUps() {
        List<CustomerFollowUp> due = followUpRepository
                .findTop100ByStatusAndScheduledAtLessThanEqualAndProcessedAtIsNullAndChannelInOrderByScheduledAtAsc(
                        "PENDING",
                        LocalDateTime.now(),
                        AUTOMATIC_CHANNELS
                );

        int processed = 0;
        for (CustomerFollowUp item : due) {
            try {
                processOne(item);
                processed++;
            } catch (Exception e) {
                markFailed(item, e.getMessage());
                log.warn("CUSTOMER FOLLOW-UP FAILED => followUpId={}, error={}", item.getId(), e.getMessage());
            }
        }
        return processed;
    }

    private void processOne(CustomerFollowUp item) {
        if (item.getCustomer() == null || item.getTenant() == null) {
            markSkipped(item, "Seguimiento sin cliente o negocio asociado");
            return;
        }

        String channel = normalizeChannel(item.getChannel());
        Customer customer = item.getCustomer();
        boolean sendPush = shouldSendPush(channel);
        boolean sendWhatsapp = shouldSendWhatsapp(channel);
        String whatsappSkipReason = null;

        if (sendWhatsapp && !canSendRetentionWhatsapp(customer)) {
            whatsappSkipReason = "Cliente sin permiso marketing WhatsApp o con baja total";
            sendWhatsapp = false;
        }

        if (sendWhatsapp && cleanPhone(customer.getTelefono()).isBlank()) {
            whatsappSkipReason = "Cliente sin telefono valido para WhatsApp";
            sendWhatsapp = false;
        }

        if (!sendPush && !sendWhatsapp) {
            markSkipped(item, whatsappSkipReason != null ? whatsappSkipReason : "Sin canales automaticos disponibles");
            return;
        }

        if (notificationRepository.existsByTypeAndReferenceTypeAndReferenceId(
                NotificationType.CUSTOMER_FOLLOW_UP,
                "CUSTOMER_FOLLOW_UP",
                item.getId()
        )) {
            item.setStatus("SENT");
            item.setProcessedAt(LocalDateTime.now());
            item.setCompletedAt(LocalDateTime.now());
            item.setLastError(whatsappSkipReason != null ? limit(whatsappSkipReason, 500) : null);
            followUpRepository.save(item);
            return;
        }

        Notification notification = Notification.builder()
                .tenant(item.getTenant())
                .branch(null)
                .customer(customer)
                .user(null)
                .type(NotificationType.CUSTOMER_FOLLOW_UP)
                .title(limit(item.getTitle(), 150))
                .message(limit(item.getMessage(), 500))
                .referenceType("CUSTOMER_FOLLOW_UP")
                .referenceId(item.getId())
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();

        notificationRepository.save(notification);

        notificationDeliveryRepository.save(NotificationDelivery.builder()
                .notification(notification)
                .channel(NotificationChannel.IN_APP)
                .status(NotificationDeliveryStatus.SENT)
                .attempts(0)
                .sentAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build());

        if (sendPush) {
            createPendingDelivery(notification, NotificationChannel.PUSH);
        }

        if (sendWhatsapp) {
            createPendingDelivery(notification, NotificationChannel.WHATSAPP);
        }

        item.setNotification(notification);
        item.setStatus("SENT");
        item.setProcessedAt(LocalDateTime.now());
        item.setCompletedAt(LocalDateTime.now());
        item.setLastError(whatsappSkipReason != null ? limit(whatsappSkipReason, 500) : null);
        followUpRepository.save(item);
    }

    private boolean canSendRetentionWhatsapp(Customer customer) {
        if (customer == null) return false;
        if (customer.getWhatsappOptedOutAt() != null) return false;
        return Boolean.TRUE.equals(customer.getWhatsappMarketingEnabled());
    }

    private boolean shouldSendPush(String channel) {
        return "PUSH".equals(channel) || "BOTH".equals(channel) || "WHATSAPP_PUSH".equals(channel);
    }

    private boolean shouldSendWhatsapp(String channel) {
        return "WHATSAPP".equals(channel) || "BOTH".equals(channel) || "WHATSAPP_PUSH".equals(channel);
    }

    private void createPendingDelivery(Notification notification, NotificationChannel channel) {
        notificationDeliveryRepository.save(NotificationDelivery.builder()
                .notification(notification)
                .channel(channel)
                .status(NotificationDeliveryStatus.PENDING)
                .attempts(0)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private String normalizeChannel(String value) {
        if (value == null || value.isBlank()) return "WHATSAPP";
        return value.trim().toUpperCase();
    }

    private String cleanPhone(String value) {
        if (value == null) return "";
        return value.replaceAll("[^0-9]", "").trim();
    }

    private void markSkipped(CustomerFollowUp item, String reason) {
        item.setStatus("SKIPPED");
        item.setProcessedAt(LocalDateTime.now());
        item.setLastError(limit(reason, 500));
        followUpRepository.save(item);
    }

    private void markFailed(CustomerFollowUp item, String reason) {
        item.setStatus("FAILED");
        item.setProcessedAt(LocalDateTime.now());
        item.setLastError(limit(reason == null ? "Error procesando seguimiento" : reason, 500));
        followUpRepository.save(item);
    }

    private String limit(String value, int max) {
        if (value == null) return "";
        if (value.length() <= max) return value;
        return value.substring(0, max);
    }
}
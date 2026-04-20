package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.response.NotificationResponse;
import com.gods.saas.domain.model.Notification;
import com.gods.saas.domain.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationQueryService {

    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public List<NotificationResponse> getUserNotifications(Long tenantId, Long userId) {
        return notificationRepository
                .findByTenant_IdAndUser_IdOrderByCreatedAtDesc(tenantId, userId)
                .stream()
                .map(NotificationResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getUserUnreadCount(Long tenantId, Long userId) {
        long count = notificationRepository.countByTenant_IdAndUser_IdAndIsReadFalse(tenantId, userId);
        return Map.of("count", count);
    }

    public void markUserNotificationAsRead(Long tenantId, Long userId, Long notificationId) {
        Notification notification = notificationRepository
                .findByIdAndTenant_IdAndUser_Id(notificationId, tenantId, userId)
                .orElseThrow(() -> new RuntimeException("Notificación no encontrada"));

        notification.setIsRead(true);
        notificationRepository.save(notification);
    }

    public void markAllUserNotificationsAsRead(Long tenantId, Long userId) {
        List<Notification> unread = notificationRepository
                .findByTenant_IdAndUser_IdAndIsReadFalse(tenantId, userId);

        unread.forEach(n -> n.setIsRead(true));
        notificationRepository.saveAll(unread);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getCustomerNotifications(Long tenantId, Long customerId) {
        return notificationRepository
                .findByTenant_IdAndCustomer_IdOrderByCreatedAtDesc(tenantId, customerId)
                .stream()
                .map(NotificationResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getCustomerUnreadCount(Long tenantId, Long customerId) {
        long count = notificationRepository.countByTenant_IdAndCustomer_IdAndIsReadFalse(tenantId, customerId);
        return Map.of("count", count);
    }

    public void markCustomerNotificationAsRead(Long tenantId, Long customerId, Long notificationId) {
        Notification notification = notificationRepository
                .findByIdAndTenant_IdAndCustomer_Id(notificationId, tenantId, customerId)
                .orElseThrow(() -> new RuntimeException("Notificación no encontrada"));

        notification.setIsRead(true);
        notificationRepository.save(notification);
    }

    public void markAllCustomerNotificationsAsRead(Long tenantId, Long customerId) {
        List<Notification> unread = notificationRepository
                .findByTenant_IdAndCustomer_IdAndIsReadFalse(tenantId, customerId);

        unread.forEach(n -> n.setIsRead(true));
        notificationRepository.saveAll(unread);
    }
}
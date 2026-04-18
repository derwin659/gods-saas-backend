package com.gods.saas.web.controller;

import com.gods.saas.domain.model.Notification;
import com.gods.saas.domain.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/owner/notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;

    @GetMapping
    public List<Notification> myNotifications(Authentication authentication) {
        Map<String, Object> details = (Map<String, Object>) authentication.getDetails();
        Long tenantId = Long.valueOf(details.get("tenantId").toString());
        Long userId = Long.valueOf(details.get("userId").toString());

        return notificationRepository.findByTenant_IdAndUser_IdOrderByCreatedAtDesc(tenantId, userId);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(Authentication authentication) {
        Map<String, Object> details = (Map<String, Object>) authentication.getDetails();
        Long tenantId = Long.valueOf(details.get("tenantId").toString());
        Long userId = Long.valueOf(details.get("userId").toString());

        long count = notificationRepository.countByTenant_IdAndUser_IdAndIsReadFalse(tenantId, userId);
        return Map.of("count", count);
    }
}
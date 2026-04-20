package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.response.NotificationResponse;
import com.gods.saas.domain.model.Notification;
import com.gods.saas.domain.repository.NotificationRepository;
import com.gods.saas.service.impl.NotificationQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/owner/notifications")
public class NotificationController {


    private final NotificationQueryService notificationQueryService;




    @GetMapping
    public List<NotificationResponse> myNotifications(Authentication authentication) {
        Map<String, Object> details = (Map<String, Object>) authentication.getDetails();
        Long tenantId = Long.valueOf(details.get("tenantId").toString());
        Long userId = Long.valueOf(details.get("userId").toString());

        return notificationQueryService.getUserNotifications(tenantId, userId);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(Authentication authentication) {
        Map<String, Object> details = (Map<String, Object>) authentication.getDetails();
        Long tenantId = Long.valueOf(details.get("tenantId").toString());
        Long userId = Long.valueOf(details.get("userId").toString());

        return notificationQueryService.getUserUnreadCount(tenantId, userId);
    }

    @PutMapping("/{id}/read")
    public Map<String, String> markAsRead(
            @PathVariable Long id,
            Authentication authentication
    ) {
        Map<String, Object> details = (Map<String, Object>) authentication.getDetails();
        Long tenantId = Long.valueOf(details.get("tenantId").toString());
        Long userId = Long.valueOf(details.get("userId").toString());

        notificationQueryService.markUserNotificationAsRead(tenantId, userId, id);
        return Map.of("message", "Notificación marcada como leída");
    }

    @PutMapping("/read-all")
    public Map<String, String> markAllAsRead(Authentication authentication) {
        Map<String, Object> details = (Map<String, Object>) authentication.getDetails();
        Long tenantId = Long.valueOf(details.get("tenantId").toString());
        Long userId = Long.valueOf(details.get("userId").toString());

        notificationQueryService.markAllUserNotificationsAsRead(tenantId, userId);
        return Map.of("message", "Todas las notificaciones fueron marcadas como leídas");
    }
}
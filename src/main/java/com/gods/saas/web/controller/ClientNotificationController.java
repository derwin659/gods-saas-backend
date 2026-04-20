package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.response.NotificationResponse;
import com.gods.saas.service.impl.NotificationQueryService;
import com.gods.saas.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clients/notifications")
public class ClientNotificationController {

    private final NotificationQueryService notificationQueryService;
    private final JwtUtil jwtUtil;

    @GetMapping
    public List<NotificationResponse> myNotifications(
            @RequestHeader("Authorization") String authHeader
    ) {
        String token = authHeader.replace("Bearer ", "");
        Long tenantId = jwtUtil.getTenantIdFromToken(token);
        Long customerId = jwtUtil.getCustomerIdFromToken(token);

        return notificationQueryService.getCustomerNotifications(tenantId, customerId);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(
            @RequestHeader("Authorization") String authHeader
    ) {
        String token = authHeader.replace("Bearer ", "");
        Long tenantId = jwtUtil.getTenantIdFromToken(token);
        Long customerId = jwtUtil.getCustomerIdFromToken(token);

        return notificationQueryService.getCustomerUnreadCount(tenantId, customerId);
    }

    @PutMapping("/{id}/read")
    public Map<String, String> markAsRead(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader
    ) {
        String token = authHeader.replace("Bearer ", "");
        Long tenantId = jwtUtil.getTenantIdFromToken(token);
        Long customerId = jwtUtil.getCustomerIdFromToken(token);

        notificationQueryService.markCustomerNotificationAsRead(tenantId, customerId, id);
        return Map.of("message", "Notificación marcada como leída");
    }

    @PutMapping("/read-all")
    public Map<String, String> markAllAsRead(
            @RequestHeader("Authorization") String authHeader
    ) {
        String token = authHeader.replace("Bearer ", "");
        Long tenantId = jwtUtil.getTenantIdFromToken(token);
        Long customerId = jwtUtil.getCustomerIdFromToken(token);

        notificationQueryService.markAllCustomerNotificationsAsRead(tenantId, customerId);
        return Map.of("message", "Todas las notificaciones fueron marcadas como leídas");
    }
}
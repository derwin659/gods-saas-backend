package com.gods.saas.domain.dto.response;

import com.gods.saas.domain.model.CustomerFollowUp;

import java.time.LocalDateTime;

public record CustomerFollowUpResponse(
        Long id,
        Long customerId,
        Long actorUserId,
        String actorName,
        String title,
        String message,
        String channel,
        String status,
        LocalDateTime scheduledAt,
        LocalDateTime completedAt,
        LocalDateTime processedAt,
        String lastError,
        Long notificationId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static CustomerFollowUpResponse from(CustomerFollowUp item) {
        String actorName = null;
        Long actorUserId = null;
        if (item.getActorUser() != null) {
            actorUserId = item.getActorUser().getId();
            String nombre = item.getActorUser().getNombre() == null ? "" : item.getActorUser().getNombre().trim();
            String apellido = item.getActorUser().getApellido() == null ? "" : item.getActorUser().getApellido().trim();
            actorName = (nombre + " " + apellido).trim();
            if (actorName.isBlank()) actorName = item.getActorUser().getEmail();
        }
        return new CustomerFollowUpResponse(
                item.getId(),
                item.getCustomer() != null ? item.getCustomer().getId() : null,
                actorUserId,
                actorName,
                item.getTitle(),
                item.getMessage(),
                item.getChannel(),
                item.getStatus(),
                item.getScheduledAt(),
                item.getCompletedAt(),
                item.getProcessedAt(),
                item.getLastError(),
                item.getNotification() != null ? item.getNotification().getId() : null,
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}

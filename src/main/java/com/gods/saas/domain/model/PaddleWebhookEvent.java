package com.gods.saas.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "paddle_webhook_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaddleWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "paddle_object_id")
    private String paddleObjectId;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;
}

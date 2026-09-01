package com.gods.saas.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_generation_job")
@Getter
@Setter
public class AiGenerationJob {

    @Id
    @Column(length = 50)
    private String id;

    @Column(name = "session_id", nullable = false, length = 50)
    private String sessionId;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(nullable = false, length = 24)
    private String status;

    @Column(name = "provider_job_id", length = 120)
    private String providerJobId;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "error_message", length = 1200)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = "AIJ-" + UUID.randomUUID();
        if (status == null) status = "QUEUED";
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void markRunning() {
        status = "RUNNING";
        attempts++;
        startedAt = LocalDateTime.now();
        errorMessage = null;
    }

    public void markCompleted() {
        status = "COMPLETED";
        completedAt = LocalDateTime.now();
        errorMessage = null;
    }

    public void markFailed(String message) {
        status = "FAILED";
        completedAt = LocalDateTime.now();
        errorMessage = message == null ? "Error desconocido" : message.substring(0, Math.min(message.length(), 1200));
    }
}
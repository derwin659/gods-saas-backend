package com.gods.saas.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "general_audit_log", indexes = {
    @Index(name = "idx_general_audit_tenant_created", columnList = "tenant_id, created_at"),
    @Index(name = "idx_general_audit_actor", columnList = "tenant_id, actor_user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeneralAuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "general_audit_log_id")
    private Long id;
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @Column(name = "branch_id")
    private Long branchId;
    @Column(name = "actor_user_id")
    private Long actorUserId;
    @Column(name = "actor_user_name", length = 180)
    private String actorUserName;
    @Column(name = "actor_role", length = 40)
    private String actorRole;
    @Column(name = "entity_type", length = 60, nullable = false)
    private String entityType;
    @Column(name = "entity_id")
    private Long entityId;
    @Column(name = "action", length = 60, nullable = false)
    private String action;
    @Column(name = "reason", length = 500)
    private String reason;
    @Column(name = "before_snapshot", columnDefinition = "text")
    private String beforeSnapshot;
    @Column(name = "after_snapshot", columnDefinition = "text")
    private String afterSnapshot;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}

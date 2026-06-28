package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@Builder
public class GeneralAuditLogResponse {
    private Long id;
    private Long branchId;
    private Long actorUserId;
    private String actorUserName;
    private String actorRole;
    private String entityType;
    private Long entityId;
    private String action;
    private String reason;
    private String beforeSnapshot;
    private String afterSnapshot;
    private LocalDateTime createdAt;
}

package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class CashAuditLogResponse {
    private Long id;
    private Long cashRegisterId;
    private String entityType;
    private Long entityId;
    private String action;
    private String reason;
    private String beforeSnapshot;
    private String afterSnapshot;
    private LocalDateTime createdAt;
    private Long actorUserId;
    private String actorUserName;
}
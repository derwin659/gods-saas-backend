package com.gods.saas.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gods.saas.domain.dto.response.GeneralAuditLogResponse;
import com.gods.saas.domain.model.AppUser;
import com.gods.saas.domain.model.GeneralAuditLog;
import com.gods.saas.domain.repository.AppUserRepository;
import com.gods.saas.domain.repository.GeneralAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class GeneralAuditService {
    private final GeneralAuditLogRepository repository;
    private final AppUserRepository appUserRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void record(Long tenantId, Long branchId, Long actorUserId, String actorRole,
                       String entityType, Long entityId, String action, String reason,
                       Object before, Object after) {
        AppUser actor = actorUserId == null ? null : appUserRepository
                .findByIdAndTenant_Id(actorUserId, tenantId).orElse(null);
        repository.save(GeneralAuditLog.builder()
                .tenantId(tenantId)
                .branchId(branchId)
                .actorUserId(actorUserId)
                .actorUserName(actor == null ? null : actor.getNombre())
                .actorRole(actorRole == null && actor != null ? actor.getRol() : actorRole)
                .entityType(normalizeRequired(entityType))
                .entityId(entityId)
                .action(normalizeRequired(action))
                .reason(blankToNull(reason))
                .beforeSnapshot(toJson(before))
                .afterSnapshot(toJson(after))
                .build());
    }

    @Transactional(readOnly = true)
    public List<GeneralAuditLogResponse> search(Long tenantId, Long branchId, Long actorUserId,
                                                String entityType, String action,
                                                LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new IllegalArgumentException("Rango de fechas invalido");
        }
        return repository.search(
                tenantId, branchId, actorUserId, normalizeOptional(entityType),
                normalizeOptional(action), from.atStartOfDay(),
                to.plusDays(1).atStartOfDay().minusNanos(1)
        ).stream().map(this::toResponse).toList();
    }

    private GeneralAuditLogResponse toResponse(GeneralAuditLog log) {
        return GeneralAuditLogResponse.builder()
                .id(log.getId())
                .branchId(log.getBranchId())
                .actorUserId(log.getActorUserId())
                .actorUserName(log.getActorUserName())
                .actorRole(log.getActorRole())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .action(log.getAction())
                .reason(log.getReason())
                .beforeSnapshot(log.getBeforeSnapshot())
                .afterSnapshot(log.getAfterSnapshot())
                .createdAt(log.getCreatedAt())
                .build();
    }

    private String toJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo serializar la auditoria", ex);
        }
    }

    private String normalizeRequired(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) throw new IllegalArgumentException("Dato de auditoria obligatorio");
        return normalized;
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

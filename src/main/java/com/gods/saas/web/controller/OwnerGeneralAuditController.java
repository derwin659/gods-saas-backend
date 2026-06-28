package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.response.GeneralAuditLogResponse;
import com.gods.saas.service.impl.AdminPermissionService;
import com.gods.saas.service.impl.GeneralAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/owner/audit-logs")
@RequiredArgsConstructor
public class OwnerGeneralAuditController {
    private final GeneralAuditService generalAuditService;
    private final AdminPermissionService adminPermissionService;

    @GetMapping
    public List<GeneralAuditLogResponse> search(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long actorUserId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String action,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        adminPermissionService.requireOwner();
        return generalAuditService.search(
                tenantId, branchId, actorUserId, entityType, action, from, to
        );
    }
}

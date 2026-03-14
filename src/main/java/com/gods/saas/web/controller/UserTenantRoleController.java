package com.gods.saas.web.controller;

import com.gods.saas.domain.model.RoleType;
import com.gods.saas.service.impl.UserTenantRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/api/user-tenant")
@RequiredArgsConstructor
public class UserTenantRoleController {

    private final UserTenantRoleService service;

    @PostMapping("/assign")
    public ResponseEntity<?> assign(
            @RequestParam Long userId,
            @RequestParam Long tenantId,
            @RequestParam RoleType role
    ) {
        return ResponseEntity.ok(service.assignRole(userId, tenantId, role));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getTenants(@PathVariable Long userId) {
        return ResponseEntity.ok(service.getTenantsOfUser(userId));
    }
}


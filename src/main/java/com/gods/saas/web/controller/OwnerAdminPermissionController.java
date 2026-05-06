package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.UpdateAdminPermissionsRequest;
import com.gods.saas.domain.dto.response.AdminPermissionsBundleResponse;
import com.gods.saas.service.impl.AdminPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/owner/admin-permissions")
@RequiredArgsConstructor
public class OwnerAdminPermissionController {

    private final AdminPermissionService adminPermissionService;

    @GetMapping("/me")
    public AdminPermissionsBundleResponse myPermissions() {
        return adminPermissionService.getMyPermissions();
    }

    @GetMapping("/{adminUserId}")
    public AdminPermissionsBundleResponse getAdminPermissions(
            @PathVariable Long adminUserId
    ) {
        return adminPermissionService.getPermissionsForAdmin(adminUserId);
    }

    @PutMapping("/{adminUserId}")
    public AdminPermissionsBundleResponse updateAdminPermissions(
            @PathVariable Long adminUserId,
            @RequestBody UpdateAdminPermissionsRequest request
    ) {
        return adminPermissionService.updatePermissionsForAdmin(adminUserId, request);
    }
}
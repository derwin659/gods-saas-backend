package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.RegisterDeviceTokenRequest;
import com.gods.saas.domain.dto.response.DeviceTokenResponse;
import com.gods.saas.service.impl.impl.DeviceTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/owner/device-tokens")
public class OwnerDeviceTokenController {

    private final DeviceTokenService deviceTokenService;

    @PostMapping
    public DeviceTokenResponse register(
            @RequestBody RegisterDeviceTokenRequest request,
            Authentication authentication
    ) {
        Map<String, Object> details = (Map<String, Object>) authentication.getDetails();
        Long tenantId = Long.valueOf(details.get("tenantId").toString());
        Long userId = Long.valueOf(details.get("userId").toString());

        return deviceTokenService.registerUserToken(tenantId, userId, request);
    }

    @DeleteMapping
    public Map<String, String> deactivate(
            @RequestParam String token,
            Authentication authentication
    ) {
        Map<String, Object> details = (Map<String, Object>) authentication.getDetails();
        Long tenantId = Long.valueOf(details.get("tenantId").toString());
        Long userId = Long.valueOf(details.get("userId").toString());

        deviceTokenService.deactivateUserToken(tenantId, userId, token);
        return Map.of("message", "Token del usuario desactivado correctamente");
    }
}
package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.RegisterDeviceTokenRequest;
import com.gods.saas.domain.dto.response.DeviceTokenResponse;
import com.gods.saas.service.impl.impl.DeviceTokenService;
import com.gods.saas.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clients/device-tokens")
public class ClientDeviceTokenController {

    private final DeviceTokenService deviceTokenService;
    private final JwtUtil jwtUtil;

    @PostMapping
    public DeviceTokenResponse register(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody RegisterDeviceTokenRequest request
    ) {
        String token = authHeader.replace("Bearer ", "");
        Long tenantId = jwtUtil.getTenantIdFromToken(token);
        Long customerId = jwtUtil.getCustomerIdFromToken(token);

        return deviceTokenService.registerCustomerToken(tenantId, customerId, request);
    }

    @DeleteMapping
    public Map<String, String> deactivate(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam String token
    ) {
        String jwt = authHeader.replace("Bearer ", "");
        Long tenantId = jwtUtil.getTenantIdFromToken(jwt);
        Long customerId = jwtUtil.getCustomerIdFromToken(jwt);

        deviceTokenService.deactivateCustomerToken(tenantId, customerId, token);
        return Map.of("message", "Token del cliente desactivado correctamente");
    }
}
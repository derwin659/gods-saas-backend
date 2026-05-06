package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.BarberCreateRequest;
import com.gods.saas.domain.dto.request.BarberStatusRequest;
import com.gods.saas.domain.dto.request.BarberUpdateRequest;
import com.gods.saas.domain.dto.response.BarberResponse;
import com.gods.saas.service.impl.AdminPermissionService;
import com.gods.saas.service.impl.JwtService;
import com.gods.saas.service.impl.impl.OwnerBarberService;
import com.gods.saas.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/owner/barbers")
@RequiredArgsConstructor
public class OwnerBarberController {

    private final OwnerBarberService ownerBarberService;
    private final JwtService jwtUtil;
    private final AdminPermissionService adminPermissionService;

    @GetMapping
    public List<BarberResponse> listBarbers(
            @RequestParam(required = false) Long branchId,
            HttpServletRequest request
    ) {
        SessionData session = extractSession(request);
        checkConfigBarbers(session);

        return ownerBarberService.listBarbers(session.tenantId(), branchId);
    }

    @PostMapping
    public BarberResponse createBarber(
            @Valid @RequestBody BarberCreateRequest requestBody,
            HttpServletRequest request
    ) {
        SessionData session = extractSession(request);
        checkConfigBarbers(session);

        return ownerBarberService.createBarber(session.tenantId(), requestBody);
    }

    @PutMapping("/{barberId}")
    public BarberResponse updateBarber(
            @PathVariable Long barberId,
            @Valid @RequestBody BarberUpdateRequest requestBody,
            HttpServletRequest request
    ) {
        SessionData session = extractSession(request);
        checkConfigBarbers(session);

        return ownerBarberService.updateBarber(session.tenantId(), barberId, requestBody);
    }

    @PatchMapping("/{barberId}/status")
    public BarberResponse updateStatus(
            @PathVariable Long barberId,
            @Valid @RequestBody BarberStatusRequest requestBody,
            HttpServletRequest request
    ) {
        SessionData session = extractSession(request);
        checkConfigBarbers(session);

        return ownerBarberService.updateStatus(session.tenantId(), barberId, requestBody);
    }

    @PostMapping(value = "/{barberId}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BarberResponse uploadPhoto(
            @PathVariable Long barberId,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request
    ) {
        SessionData session = extractSession(request);
        checkConfigBarbers(session);

        return ownerBarberService.uploadPhoto(session.tenantId(), barberId, file);
    }

    @DeleteMapping("/{barberId}/photo")
    public BarberResponse deletePhoto(
            @PathVariable Long barberId,
            HttpServletRequest request
    ) {
        SessionData session = extractSession(request);
        checkConfigBarbers(session);

        return ownerBarberService.deletePhoto(session.tenantId(), barberId);
    }

    private void checkConfigBarbers(SessionData session) {
        if ("OWNER".equalsIgnoreCase(session.role())) {
            return;
        }

        boolean allowed = adminPermissionService.hasPermission(
                session.tenantId(),
                session.userId(),
                "CONFIG_BARBERS"
        );

        if (!allowed) {
            throw new AccessDeniedException("No tienes permiso para esta acción");
        }
    }

    private SessionData extractSession(HttpServletRequest request) {
        Long tenantId = getLongRequestAttribute(request, "tenantId");
        Long userId = getLongRequestAttribute(request, "userId");
        String role = getStringRequestAttribute(request, "role");

        if (tenantId != null && userId != null && role != null && !role.isBlank()) {
            return new SessionData(tenantId, userId, role);
        }

        String token = extractToken(request);

        Map<String, Object> claims = jwtUtil.extractAllClaims(token);

        tenantId = toLong(claims.get("tenantId"));
        userId = toLong(claims.get("userId"));
        role = claims.get("role") == null ? "" : claims.get("role").toString();

        if (tenantId == null) {
            throw new IllegalArgumentException("No se encontró tenantId en la sesión.");
        }

        if (userId == null) {
            throw new IllegalArgumentException("No se encontró userId en la sesión.");
        }

        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("No se encontró role en la sesión.");
        }

        return new SessionData(tenantId, userId, role);
    }

    private Long getLongRequestAttribute(HttpServletRequest request, String key) {
        Object value = request.getAttribute(key);
        return toLong(value);
    }

    private String getStringRequestAttribute(HttpServletRequest request, String key) {
        Object value = request.getAttribute(key);
        return value == null ? null : value.toString();
    }

    private Long toLong(Object value) {
        if (value == null) return null;

        if (value instanceof Number n) {
            return n.longValue();
        }

        try {
            return Long.parseLong(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Token JWT no enviado o inválido.");
        }

        return authHeader.substring(7);
    }

    private record SessionData(
            Long tenantId,
            Long userId,
            String role
    ) {
    }
}
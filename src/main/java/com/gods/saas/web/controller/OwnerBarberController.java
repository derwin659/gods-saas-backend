package com.gods.saas.web.controller;

import com.gods.saas.security.BranchAccessGuard;

import com.gods.saas.domain.dto.request.BarberCreateRequest;
import com.gods.saas.domain.dto.request.BarberStatusRequest;
import com.gods.saas.domain.dto.request.BarberUpdateRequest;
import com.gods.saas.domain.dto.request.DeleteBarberRequest;
import com.gods.saas.domain.dto.request.OwnerProfessionalProfileRequest;
import com.gods.saas.domain.dto.request.UpdateBarberServicesRequest;
import com.gods.saas.domain.dto.request.UpdateBarberServiceCommissionsRequest;
import com.gods.saas.domain.dto.response.BarberServiceAssignmentResponse;
import com.gods.saas.domain.dto.response.BarberServiceCommissionResponse;
import com.gods.saas.domain.dto.response.BarberResponse;
import com.gods.saas.domain.dto.response.BarberDeletionPreviewResponse;
import com.gods.saas.domain.dto.response.BarberDeletionResponse;
import com.gods.saas.service.impl.AdminPermissionService;
import com.gods.saas.service.impl.BarberServiceAssignmentService;
import com.gods.saas.service.impl.BarberServiceCommissionService;
import com.gods.saas.service.impl.BarberSafeDeletionService;
import com.gods.saas.service.impl.OwnerProfessionalProfileService;
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
    private final BranchAccessGuard branchAccessGuard;
    private final BarberServiceAssignmentService barberServiceAssignmentService;
    private final BarberServiceCommissionService barberServiceCommissionService;
    private final OwnerProfessionalProfileService ownerProfessionalProfileService;
    private final BarberSafeDeletionService barberSafeDeletionService;

    @GetMapping
    public List<BarberResponse> listBarbers(
            @RequestParam(required = false) Long branchId,
            HttpServletRequest request
    ) {
        SessionData session = extractSession(request);
        checkConfigBarbers(session);

        Long effectiveBranchId = branchAccessGuard.resolveOptionalForOwner(branchId, session.branchId());
        return ownerBarberService.listBarbers(session.tenantId(), effectiveBranchId);
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

        return ownerBarberService.updateBarber(
                session.tenantId(), session.userId(), session.role(), barberId, requestBody
        );
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

    @GetMapping("/{barberId}/deletion-preview")
    public BarberDeletionPreviewResponse deletionPreview(@PathVariable Long barberId, HttpServletRequest request) {
        SessionData session = extractSession(request);
        checkConfigBarbers(session);
        return barberSafeDeletionService.preview(session.tenantId(), barberId);
    }

    @DeleteMapping("/{barberId}")
    public BarberDeletionResponse deleteBarber(@PathVariable Long barberId,
            @Valid @RequestBody DeleteBarberRequest body, HttpServletRequest request) {
        SessionData session = extractSession(request);
        checkConfigBarbers(session);
        return barberSafeDeletionService.delete(session.tenantId(), session.userId(), session.role(), barberId, body);
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

    @GetMapping("/self-professional")
    public Map<String,Object> getSelfProfessional(HttpServletRequest request) {
        SessionData session = extractSession(request);
        if (!"OWNER".equalsIgnoreCase(session.role())) throw new AccessDeniedException("Solo el dueño puede configurar su perfil profesional");
        return ownerProfessionalProfileService.status(session.tenantId(), session.userId());
    }

    @PutMapping("/self-professional")
    public Map<String,Object> enableSelfProfessional(@RequestBody OwnerProfessionalProfileRequest body, HttpServletRequest request) {
        SessionData session = extractSession(request);
        if (!"OWNER".equalsIgnoreCase(session.role())) throw new AccessDeniedException("Solo el dueño puede configurar su perfil profesional");
        return ownerProfessionalProfileService.enable(session.tenantId(), session.userId(), body);
    }

    @DeleteMapping("/self-professional")
    public Map<String,Object> disableSelfProfessional(HttpServletRequest request) {
        SessionData session = extractSession(request);
        if (!"OWNER".equalsIgnoreCase(session.role())) throw new AccessDeniedException("Solo el dueño puede configurar su perfil profesional");
        return ownerProfessionalProfileService.disable(session.tenantId(), session.userId());
    }

    @GetMapping("/{barberId}/services")
    public BarberServiceAssignmentResponse getServices(@PathVariable Long barberId, @RequestParam Long branchId, HttpServletRequest request) {
        SessionData session = extractSession(request);
        checkConfigBarbers(session);
        Long allowedBranchId = branchAccessGuard.resolve(branchId, session.branchId());
        return barberServiceAssignmentService.get(session.tenantId(), allowedBranchId, barberId);
    }

    @PutMapping("/{barberId}/services")
    public BarberServiceAssignmentResponse updateServices(@PathVariable Long barberId, @RequestParam Long branchId,
            @RequestBody UpdateBarberServicesRequest body, HttpServletRequest request) {
        SessionData session = extractSession(request);
        checkConfigBarbers(session);
        Long allowedBranchId = branchAccessGuard.resolve(branchId, session.branchId());
        return barberServiceAssignmentService.update(session.tenantId(), allowedBranchId, barberId,
                session.userId(), session.role(), body == null ? null : body.getServiceIds());
    }

    @DeleteMapping("/{barberId}/services")
    public BarberServiceAssignmentResponse resetServices(@PathVariable Long barberId, @RequestParam Long branchId, HttpServletRequest request) {
        SessionData session = extractSession(request);
        checkConfigBarbers(session);
        Long allowedBranchId = branchAccessGuard.resolve(branchId, session.branchId());
        return barberServiceAssignmentService.reset(session.tenantId(), allowedBranchId, barberId, session.userId(), session.role());
    }


    @GetMapping("/{barberId}/service-commissions")
    public BarberServiceCommissionResponse getServiceCommissions(@PathVariable Long barberId, @RequestParam Long branchId, HttpServletRequest request) {
        SessionData session = extractSession(request);
        checkConfigBarbers(session);
        Long allowedBranchId = branchAccessGuard.resolve(branchId, session.branchId());
        return barberServiceCommissionService.get(session.tenantId(), allowedBranchId, barberId);
    }

    @PutMapping("/{barberId}/service-commissions")
    public BarberServiceCommissionResponse updateServiceCommissions(@PathVariable Long barberId, @RequestParam Long branchId,
            @RequestBody UpdateBarberServiceCommissionsRequest body, HttpServletRequest request) {
        SessionData session = extractSession(request);
        checkConfigBarbers(session);
        Long allowedBranchId = branchAccessGuard.resolve(branchId, session.branchId());
        return barberServiceCommissionService.update(session.tenantId(), allowedBranchId, barberId, session.userId(), session.role(), body);
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
        Long branchId = getLongRequestAttribute(request, "branchId");

        if (tenantId != null && userId != null && role != null && !role.isBlank()) {
            return new SessionData(tenantId, userId, role, branchId);
        }

        String token = extractToken(request);

        Map<String, Object> claims = jwtUtil.extractAllClaims(token);

        tenantId = toLong(claims.get("tenantId"));
        userId = toLong(claims.get("userId"));
        role = claims.get("role") == null ? "" : claims.get("role").toString();
        branchId = toLong(claims.get("branchId"));

        if (tenantId == null) {
            throw new IllegalArgumentException("No se encontró tenantId en la sesión.");
        }

        if (userId == null) {
            throw new IllegalArgumentException("No se encontró userId en la sesión.");
        }

        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("No se encontró role en la sesión.");
        }

        return new SessionData(tenantId, userId, role, branchId);
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
            String role,
            Long branchId
    ) {
    }
}
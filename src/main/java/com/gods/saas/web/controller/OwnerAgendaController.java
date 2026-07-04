package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.CreateOwnerAppointmentRequest;
import com.gods.saas.domain.dto.request.UpdateOwnerAppointmentRequest;
import com.gods.saas.domain.dto.response.OwnerAgendaResponse;
import com.gods.saas.domain.dto.response.OwnerAppointmentAvailabilityResponse;
import com.gods.saas.service.impl.AdminPermissionService;
import com.gods.saas.security.BranchAccessGuard;
import com.gods.saas.service.impl.JwtService;
import com.gods.saas.service.impl.OwnerAgendaAppointmentService;
import com.gods.saas.service.impl.impl.OwnerAgendaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/owner/agenda")
@RequiredArgsConstructor
public class OwnerAgendaController {

    private final OwnerAgendaService ownerAgendaService;
    private final OwnerAgendaAppointmentService ownerAgendaAppointmentService;
    private final JwtService jwtService;
    private final AdminPermissionService adminPermissionService;
    private final BranchAccessGuard branchAccessGuard;

    @GetMapping
    public List<OwnerAgendaResponse> getAgendaDelDia(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) String fecha,
            @RequestParam(required = false) Long branchId
    ) {
        adminPermissionService.checkPermission("AGENDA_ACCESS");

        SessionData session = readSession(authHeader);
        Long branchIdFinal = resolveBranchId(branchId, session.branchId());

        LocalDate fechaConsulta = (fecha == null || fecha.isBlank())
                ? LocalDate.now()
                : LocalDate.parse(fecha);

        boolean canViewPhone = adminPermissionService.hasCurrentUserPermission("CUSTOMERS_VIEW_PHONE");

        return ownerAgendaService.getAgendaDelDia(session.tenantId(), branchIdFinal, fechaConsulta)
                .stream()
                .map(item -> protectPhone(item, canViewPhone))
                .toList();
    }

    @GetMapping("/availability")
    public OwnerAppointmentAvailabilityResponse getAvailability(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) Long branchId,
            @RequestParam Long barberUserId,
            @RequestParam Long serviceId,
            @RequestParam String fecha,
            @RequestParam(required = false) Long appointmentId
    ) {
        adminPermissionService.checkPermission("AGENDA_ACCESS");

        SessionData session = readSession(authHeader);
        Long branchIdFinal = resolveBranchId(branchId, session.branchId());

        return ownerAgendaAppointmentService.getAvailability(
                session.tenantId(),
                branchIdFinal,
                barberUserId,
                serviceId,
                LocalDate.parse(fecha),
                appointmentId
        );
    }

    @PostMapping("/appointments")
    public OwnerAgendaResponse createAppointment(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) Long branchId,
            @RequestBody CreateOwnerAppointmentRequest request
    ) {
        adminPermissionService.checkPermission("AGENDA_ACCESS");

        SessionData session = readSession(authHeader);
        Long branchIdFinal = resolveBranchId(
                branchId != null ? branchId : request.getBranchId(),
                session.branchId()
        );

        boolean canViewPhone = adminPermissionService.hasCurrentUserPermission("CUSTOMERS_VIEW_PHONE");

        return protectPhone(ownerAgendaAppointmentService.createAppointment(
                session.tenantId(),
                branchIdFinal,
                request
        ), canViewPhone);
    }

    @PutMapping("/appointments/{appointmentId}")
    public OwnerAgendaResponse updateAppointment(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long appointmentId,
            @RequestParam(required = false) Long branchId,
            @RequestBody UpdateOwnerAppointmentRequest request
    ) {
        adminPermissionService.checkPermission("AGENDA_ACCESS");

        SessionData session = readSession(authHeader);
        Long branchIdFinal = resolveBranchId(branchId, session.branchId());

        boolean canViewPhone = adminPermissionService.hasCurrentUserPermission("CUSTOMERS_VIEW_PHONE");

        return protectPhone(ownerAgendaAppointmentService.updateAppointment(
                session.tenantId(),
                branchIdFinal,
                appointmentId,
                request
        ), canViewPhone);
    }

    @PostMapping("/appointments/{appointmentId}/deposit/validate")
    public OwnerAgendaResponse validateDeposit(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long appointmentId,
            @RequestParam(required = false) Long branchId,
            @RequestBody(required = false) Map<String, Object> request
    ) {
        adminPermissionService.checkPermission("AGENDA_ACCESS");

        SessionData session = readSession(authHeader);
        Long branchIdFinal = resolveBranchId(branchId, session.branchId());

        boolean approved = getBoolean(request, "approved", false);
        String note = getString(request, "note");

        boolean canViewPhone = adminPermissionService.hasCurrentUserPermission("CUSTOMERS_VIEW_PHONE");

        return protectPhone(ownerAgendaAppointmentService.validateDeposit(
                session.tenantId(),
                branchIdFinal,
                appointmentId,
                session.userId(),
                approved,
                note
        ), canViewPhone);
    }

    @DeleteMapping("/appointments/{appointmentId}")
    public OwnerAgendaResponse cancelAppointment(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long appointmentId,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) String reason
    ) {
        adminPermissionService.checkPermission("AGENDA_ACCESS");

        SessionData session = readSession(authHeader);
        Long branchIdFinal = resolveBranchId(branchId, session.branchId());

        boolean canViewPhone = adminPermissionService.hasCurrentUserPermission("CUSTOMERS_VIEW_PHONE");

        return protectPhone(ownerAgendaAppointmentService.cancelAppointment(
                session.tenantId(),
                branchIdFinal,
                appointmentId,
                reason
        ), canViewPhone);
    }
    @PostMapping("/appointments/{appointmentId}/no-show")
    public OwnerAgendaResponse markNoShow(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long appointmentId,
            @RequestParam(required = false) Long branchId,
            @RequestBody(required = false) Map<String, Object> request
    ) {
        adminPermissionService.checkPermission("AGENDA_ACCESS");

        SessionData session = readSession(authHeader);
        Long branchIdFinal = resolveBranchId(branchId, session.branchId());
        String reason = getString(request, "reason");
        boolean canViewPhone = adminPermissionService.hasCurrentUserPermission("CUSTOMERS_VIEW_PHONE");

        return protectPhone(ownerAgendaAppointmentService.markNoShow(
                session.tenantId(), branchIdFinal, appointmentId, reason
        ), canViewPhone);
    }
    private SessionData readSession(String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        Map<String, Object> claims = jwtService.extractAllClaims(token);

        Long tenantId = ((Number) claims.get("tenantId")).longValue();

        Object branchObj = claims.get("branchId");
        Long branchId = branchObj != null ? ((Number) branchObj).longValue() : null;

        Object userObj = claims.get("userId");
        Long userId = userObj != null ? ((Number) userObj).longValue() : null;

        return new SessionData(tenantId, branchId, userId);
    }

    private Long resolveBranchId(Long requestBranchId, Long sessionBranchId) {
        return branchAccessGuard.resolve(requestBranchId, sessionBranchId);
    }

    private boolean getBoolean(Map<String, Object> request, String key, boolean defaultValue) {
        if (request == null || !request.containsKey(key) || request.get(key) == null) {
            return defaultValue;
        }

        Object value = request.get(key);
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.intValue() != 0;

        String text = value.toString().trim().toLowerCase();
        return text.equals("true") || text.equals("1") || text.equals("yes") || text.equals("si") || text.equals("sí");
    }

    private String getString(Map<String, Object> request, String key) {
        if (request == null || !request.containsKey(key) || request.get(key) == null) {
            return null;
        }

        String value = request.get(key).toString().trim();
        return value.isEmpty() ? null : value;
    }

    private OwnerAgendaResponse protectPhone(OwnerAgendaResponse response, boolean canViewPhone) {
        if (canViewPhone || response == null) {
            return response;
        }

        response.setTelefono(maskPhone(response.getTelefono()));
        return response;
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "";
        }

        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() <= 4) {
            return "****";
        }

        return "****" + digits.substring(digits.length() - 4);
    }

    private record SessionData(Long tenantId, Long branchId, Long userId) {
    }
}

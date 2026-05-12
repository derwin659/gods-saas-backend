package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.CreateOwnerAppointmentRequest;
import com.gods.saas.domain.dto.request.UpdateOwnerAppointmentRequest;
import com.gods.saas.domain.dto.response.OwnerAgendaResponse;
import com.gods.saas.domain.dto.response.OwnerAppointmentAvailabilityResponse;
import com.gods.saas.service.impl.AdminPermissionService;
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

        return ownerAgendaService.getAgendaDelDia(session.tenantId(), branchIdFinal, fechaConsulta);
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

        return ownerAgendaAppointmentService.createAppointment(
                session.tenantId(),
                branchIdFinal,
                request
        );
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
        Long branchIdFinal = branchId != null ? branchId : session.branchId();

        return ownerAgendaAppointmentService.updateAppointment(
                session.tenantId(),
                branchIdFinal,
                appointmentId,
                request
        );
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
        Long branchIdFinal = branchId != null ? branchId : session.branchId();

        boolean approved = getBoolean(request, "approved", false);
        String note = getString(request, "note");

        return ownerAgendaAppointmentService.validateDeposit(
                session.tenantId(),
                branchIdFinal,
                appointmentId,
                session.userId(),
                approved,
                note
        );
    }

    @DeleteMapping("/appointments/{appointmentId}")
    public OwnerAgendaResponse cancelAppointment(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long appointmentId,
            @RequestParam(required = false) Long branchId
    ) {
        adminPermissionService.checkPermission("AGENDA_ACCESS");

        SessionData session = readSession(authHeader);
        Long branchIdFinal = branchId != null ? branchId : session.branchId();

        return ownerAgendaAppointmentService.cancelAppointment(
                session.tenantId(),
                branchIdFinal,
                appointmentId
        );
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
        Long branchIdFinal = requestBranchId != null ? requestBranchId : sessionBranchId;

        if (branchIdFinal == null) {
            throw new RuntimeException("No se encontró branchId en el token ni en la consulta.");
        }

        return branchIdFinal;
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

    private record SessionData(Long tenantId, Long branchId, Long userId) {
    }
}

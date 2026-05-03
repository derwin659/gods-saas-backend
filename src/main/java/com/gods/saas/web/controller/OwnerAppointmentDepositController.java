package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.ValidateAppointmentDepositRequest;
import com.gods.saas.domain.dto.response.CreateAppointmentResponse;

import com.gods.saas.service.impl.OwnerAppointmentDepositService;
import com.gods.saas.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/owner/appointments")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class OwnerAppointmentDepositController {

    private final OwnerAppointmentDepositService service;
    private final JwtUtil jwtUtil;

    @PostMapping("/{appointmentId}/deposit/validate")
    public ResponseEntity<CreateAppointmentResponse> validateDeposit(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long appointmentId,
            @RequestBody ValidateAppointmentDepositRequest request
    ) {
        String token = authHeader.replace("Bearer ", "");
        Long tenantId = jwtUtil.getTenantIdFromToken(token);
        Long userId = jwtUtil.getUserIdFromToken(token);

        return ResponseEntity.ok(
                service.validateDeposit(tenantId, userId, appointmentId, request)
        );
    }
}
package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.QuickRegisterCustomerRequest;
import com.gods.saas.domain.dto.request.StartWalkInAttendRequest;
import com.gods.saas.domain.dto.response.BarberServiceResponse;
import com.gods.saas.domain.dto.response.CustomerLookupResponse;
import com.gods.saas.domain.dto.response.FinishAttendResponse;
import com.gods.saas.domain.dto.response.StartAttendResponse;
import com.gods.saas.service.impl.BarberAttendService;
import com.gods.saas.utils.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/barber/attend")
@RequiredArgsConstructor
public class BarberAttendController {

    private final BarberAttendService barberAttendService;
    private final JwtUtil jwtUtil;

    @GetMapping("/customer-by-phone")
    public CustomerLookupResponse findCustomerByPhone(
            @RequestParam Long tenantId,
            @RequestParam String phone
    ) {
        return barberAttendService.findCustomerByPhone(tenantId, phone);
    }

    @PostMapping("/customers/quick-register")
    public CustomerLookupResponse quickRegister(
            @Valid @RequestBody QuickRegisterCustomerRequest request
    ) {
        return barberAttendService.quickRegister(request);
    }

    @GetMapping("/services")
    public List<BarberServiceResponse> listServices(@RequestParam Long tenantId) {
        return barberAttendService.listServices(tenantId);
    }

    @PostMapping("/start-walk-in")
    public StartAttendResponse startWalkIn(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody StartWalkInAttendRequest request
    ) {
        String token = authHeader.replace("Bearer ", "");
        Long tenantId = jwtUtil.getTenantIdFromToken(token);
        Long barberUserId = jwtUtil.getUserIdFromToken(token);

        return barberAttendService.startWalkIn(tenantId, barberUserId, request);
    }

    @PostMapping("/start-reserved/{appointmentId}")
    public StartAttendResponse startReserved(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long appointmentId
    ) {
        String token = authHeader.replace("Bearer ", "");
        Long tenantId = jwtUtil.getTenantIdFromToken(token);
        Long barberUserId = jwtUtil.getUserIdFromToken(token);

        return barberAttendService.startReserved(tenantId, barberUserId, appointmentId);
    }

    @PostMapping("/finish/{appointmentId}")
    public FinishAttendResponse finishAttend(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long appointmentId
    ) {
        String token = authHeader.replace("Bearer ", "");
        Long tenantId = jwtUtil.getTenantIdFromToken(token);
        Long barberUserId = jwtUtil.getUserIdFromToken(token);

        return barberAttendService.finishAttend(tenantId, barberUserId, appointmentId);
    }
}
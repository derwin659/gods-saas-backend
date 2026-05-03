package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.CreateAppointmentRequest;
import com.gods.saas.domain.dto.response.BookingAvailabilityResponse;
import com.gods.saas.domain.dto.response.BookingBootstrapResponse;
import com.gods.saas.domain.dto.response.CreateAppointmentResponse;
import com.gods.saas.service.impl.CloudinaryStorageService;
import com.gods.saas.utils.JwtUtil;
import com.gods.saas.service.impl.ClientBookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/clients/booking")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ClientBookingController {

    private final ClientBookingService clientBookingService;
    private final CloudinaryStorageService cloudinaryStorageService;
    private final JwtUtil jwtUtil;

    @GetMapping("/bootstrap")
    public ResponseEntity<BookingBootstrapResponse> bootstrap(
            @RequestHeader("Authorization") String authHeader
    ) {
        String token = authHeader.replace("Bearer ", "");
        Long tenantId = jwtUtil.getTenantIdFromToken(token);

        return ResponseEntity.ok(clientBookingService.getBootstrap(tenantId));
    }

    @GetMapping("/availability")
    public ResponseEntity<BookingAvailabilityResponse> availability(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam Long branchId,
            @RequestParam Long serviceId,
            @RequestParam String date,
            @RequestParam(required = false) Long barberId
    ) {
        String token = authHeader.replace("Bearer ", "");
        Long tenantId = jwtUtil.getTenantIdFromToken(token);

        return ResponseEntity.ok(
                clientBookingService.getAvailability(
                        tenantId, branchId, serviceId, date, barberId
                )
        );
    }

    @PostMapping("/deposit/evidence")
    public ResponseEntity<Map<String, Object>> uploadDepositEvidence(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam("file") MultipartFile file
    ) {
        String token = authHeader.replace("Bearer ", "");
        Long tenantId = jwtUtil.getTenantIdFromToken(token);
        Long customerId = jwtUtil.getCustomerIdFromToken(token);

        CloudinaryStorageService.UploadResult result =
                cloudinaryStorageService.uploadAppointmentDepositEvidence(
                        tenantId,
                        customerId,
                        file
                );

        return ResponseEntity.ok(Map.of(
                "url", result.getSecureUrl(),
                "publicId", result.getPublicId()
        ));
    }

    @PostMapping("/appointments")
    public ResponseEntity<CreateAppointmentResponse> createAppointment(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody CreateAppointmentRequest request
    ) {
        String token = authHeader.replace("Bearer ", "");
        Long tenantId = jwtUtil.getTenantIdFromToken(token);
        Long customerId = jwtUtil.getCustomerIdFromToken(token);

        return ResponseEntity.ok(
                clientBookingService.createAppointment(tenantId, customerId, request)
        );
    }
}

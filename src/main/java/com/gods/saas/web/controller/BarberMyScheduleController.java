package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.CreateBarberTimeBlockRequest;
import com.gods.saas.domain.dto.request.SaveBarberAvailabilityRequest;
import com.gods.saas.domain.dto.response.BarberAvailabilityDayResponse;
import com.gods.saas.domain.dto.response.BarberTimeBlockResponse;
import com.gods.saas.service.impl.BarberMyScheduleService;
import com.gods.saas.service.impl.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/barber")
@RequiredArgsConstructor
public class BarberMyScheduleController {

    private final BarberMyScheduleService barberMyScheduleService;
    private final JwtService jwtService;

    @GetMapping("/my-availability")
    public ResponseEntity<List<BarberAvailabilityDayResponse>> getMyAvailability(
            @RequestHeader("Authorization") String authHeader
    ) {
        Map<String, Object> claims = getClaims(authHeader);

        Long tenantId = extractRequiredLong(claims, "tenantId");
        Long branchId = extractRequiredLong(claims, "branchId");
        Long barberUserId = extractRequiredLong(claims, "userId");

        return ResponseEntity.ok(
                barberMyScheduleService.getMyAvailability(tenantId, branchId, barberUserId)
        );
    }

    @PostMapping("/my-availability")
    public ResponseEntity<Map<String, String>> saveMyAvailability(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody SaveBarberAvailabilityRequest request
    ) {
        Map<String, Object> claims = getClaims(authHeader);

        Long tenantId = extractRequiredLong(claims, "tenantId");
        Long branchId = extractRequiredLong(claims, "branchId");
        Long barberUserId = extractRequiredLong(claims, "userId");

        request.setBarberUserId(barberUserId);
        barberMyScheduleService.saveMyAvailability(tenantId, branchId, barberUserId, request);

        return ResponseEntity.ok(Map.of("message", "Horario guardado correctamente"));
    }

    @GetMapping("/my-time-blocks")
    public ResponseEntity<List<BarberTimeBlockResponse>> listMyBlocks(
            @RequestHeader("Authorization") String authHeader
    ) {
        Map<String, Object> claims = getClaims(authHeader);

        Long tenantId = extractRequiredLong(claims, "tenantId");
        Long branchId = extractRequiredLong(claims, "branchId");
        Long barberUserId = extractRequiredLong(claims, "userId");

        return ResponseEntity.ok(
                barberMyScheduleService.listMyBlocks(tenantId, branchId, barberUserId)
        );
    }

    @PostMapping("/my-time-blocks")
    public ResponseEntity<Map<String, String>> createMyBlock(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody CreateBarberTimeBlockRequest request
    ) {
        Map<String, Object> claims = getClaims(authHeader);

        Long tenantId = extractRequiredLong(claims, "tenantId");
        Long branchId = extractRequiredLong(claims, "branchId");
        Long barberUserId = extractRequiredLong(claims, "userId");

        request.setBarberUserId(barberUserId);
        barberMyScheduleService.createMyBlock(tenantId, branchId, barberUserId, request);

        return ResponseEntity.ok(Map.of("message", "Bloqueo creado correctamente"));
    }

    @DeleteMapping("/my-time-blocks/{blockId}")
    public ResponseEntity<Map<String, String>> deleteMyBlock(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long blockId
    ) {
        Map<String, Object> claims = getClaims(authHeader);

        Long tenantId = extractRequiredLong(claims, "tenantId");
        Long branchId = extractRequiredLong(claims, "branchId");
        Long barberUserId = extractRequiredLong(claims, "userId");

        barberMyScheduleService.deleteMyBlock(tenantId, branchId, barberUserId, blockId);

        return ResponseEntity.ok(Map.of("message", "Bloqueo eliminado correctamente"));
    }

    private Long extractRequiredLong(Map<String, Object> claims, String key) {
        Object value = claims.get(key);
        if (value == null) {
            throw new RuntimeException("No se encontró '" + key + "' en el token");
        }
        if (!(value instanceof Number)) {
            throw new RuntimeException("El claim '" + key + "' no tiene un formato válido");
        }
        return ((Number) value).longValue();
    }

    private Map<String, Object> getClaims(String authHeader) {
        if (authHeader == null || authHeader.isBlank() || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Token JWT no enviado o inválido");
        }

        String token = authHeader.substring(7).trim();
        if (token.isEmpty()) {
            throw new RuntimeException("Token JWT vacío");
        }

        return jwtService.extractAllClaims(token);
    }
}
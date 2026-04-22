package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.SaveBarberAvailabilityRequest;
import com.gods.saas.domain.dto.response.BarberAvailabilityDayResponse;
import com.gods.saas.service.impl.JwtService;
import com.gods.saas.service.impl.OwnerBarberAvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/owner/barber-availability")
@RequiredArgsConstructor
public class OwnerBarberAvailabilityController {

    private final OwnerBarberAvailabilityService ownerBarberAvailabilityService;
    private final JwtService jwtService;

    @PostMapping
    public ResponseEntity<Map<String, String>> saveAvailability(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody SaveBarberAvailabilityRequest request,
            @RequestParam(value = "branchId", required = false) Long branchIdParam
    ) {
        Map<String, Object> claims = getClaims(authHeader);
        Long tenantId = ((Number) claims.get("tenantId")).longValue();
        Long branchId = resolveBranchId(claims, branchIdParam);

        ownerBarberAvailabilityService.saveAvailability(tenantId, branchId, request);

        return ResponseEntity.ok(Map.of("message", "Horario guardado correctamente"));
    }

    @GetMapping
    public ResponseEntity<List<BarberAvailabilityDayResponse>> getAvailability(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam Long barberUserId,
            @RequestParam(value = "branchId", required = false) Long branchIdParam
    ) {
        Map<String, Object> claims = getClaims(authHeader);
        Long tenantId = ((Number) claims.get("tenantId")).longValue();
        Long branchId = resolveBranchId(claims, branchIdParam);

        return ResponseEntity.ok(
                ownerBarberAvailabilityService.getAvailability(tenantId, branchId, barberUserId)
        );
    }

    private Long resolveBranchId(Map<String, Object> claims, Long branchIdParam) {
        if (branchIdParam != null) {
            return branchIdParam;
        }

        Object branchIdClaim = claims.get("branchId");
        if (branchIdClaim == null) {
            throw new RuntimeException("No se encontró la sucursal activa");
        }

        return ((Number) branchIdClaim).longValue();
    }

    private Map<String, Object> getClaims(String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        return jwtService.extractAllClaims(token);
    }
}
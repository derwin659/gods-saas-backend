package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.CreateBarberTimeBlockRequest;
import com.gods.saas.domain.dto.response.BarberTimeBlockResponse;
import com.gods.saas.service.impl.JwtService;
import com.gods.saas.service.impl.OwnerBarberTimeBlockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/owner/barber-time-blocks")
@RequiredArgsConstructor
public class OwnerBarberTimeBlockController {

    private final OwnerBarberTimeBlockService ownerBarberTimeBlockService;
    private final JwtService jwtService;

    @PostMapping
    public ResponseEntity<Map<String, String>> createBlock(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody CreateBarberTimeBlockRequest request
    ) {
        Map<String, Object> claims = getClaims(authHeader);
        Long tenantId = ((Number) claims.get("tenantId")).longValue();
        Long branchId = ((Number) claims.get("branchId")).longValue();

        ownerBarberTimeBlockService.createBlock(tenantId, branchId, request);

        return ResponseEntity.ok(Map.of("message", "Bloqueo creado correctamente"));
    }

    @GetMapping
    public ResponseEntity<List<BarberTimeBlockResponse>> listBlocks(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam Long barberUserId
    ) {
        Map<String, Object> claims = getClaims(authHeader);
        Long tenantId = ((Number) claims.get("tenantId")).longValue();
        Long branchId = ((Number) claims.get("branchId")).longValue();

        return ResponseEntity.ok(
                ownerBarberTimeBlockService.listBlocks(tenantId, branchId, barberUserId)
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteBlock(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id
    ) {
        Map<String, Object> claims = getClaims(authHeader);
        Long tenantId = ((Number) claims.get("tenantId")).longValue();
        Long branchId = ((Number) claims.get("branchId")).longValue();

        ownerBarberTimeBlockService.deleteBlock(tenantId, branchId, id);

        return ResponseEntity.ok(Map.of("message", "Bloqueo eliminado correctamente"));
    }

    private Map<String, Object> getClaims(String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        return jwtService.extractAllClaims(token);
    }
}
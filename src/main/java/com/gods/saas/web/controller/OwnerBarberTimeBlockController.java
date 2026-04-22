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
            @RequestBody CreateBarberTimeBlockRequest request,
            @RequestParam(value = "branchId", required = false) Long branchIdParam
    ) {
        Map<String, Object> claims = getClaims(authHeader);
        Long tenantId = ((Number) claims.get("tenantId")).longValue();
        Long branchId = resolveBranchId(claims, branchIdParam);

        ownerBarberTimeBlockService.createBlock(tenantId, branchId, request);

        return ResponseEntity.ok(Map.of("message", "Bloqueo creado correctamente"));
    }

    @GetMapping
    public ResponseEntity<List<BarberTimeBlockResponse>> listBlocks(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam Long barberUserId,
            @RequestParam(value = "branchId", required = false) Long branchIdParam
    ) {
        Map<String, Object> claims = getClaims(authHeader);
        Long tenantId = ((Number) claims.get("tenantId")).longValue();
        Long branchId = resolveBranchId(claims, branchIdParam);

        return ResponseEntity.ok(
                ownerBarberTimeBlockService.listBlocks(tenantId, branchId, barberUserId)
        );
    }

    @DeleteMapping("/{blockId}")
    public ResponseEntity<Map<String, String>> deleteBlock(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long blockId,
            @RequestParam(value = "branchId", required = false) Long branchIdParam
    ) {
        Map<String, Object> claims = getClaims(authHeader);
        Long tenantId = ((Number) claims.get("tenantId")).longValue();
        Long branchId = resolveBranchId(claims, branchIdParam);

        ownerBarberTimeBlockService.deleteBlock(tenantId, branchId, blockId);

        return ResponseEntity.ok(Map.of("message", "Bloqueo eliminado correctamente"));
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
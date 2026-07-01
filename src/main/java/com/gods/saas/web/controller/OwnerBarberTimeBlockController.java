package com.gods.saas.web.controller;

import com.gods.saas.security.BranchAccessGuard;

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
    private final BranchAccessGuard branchAccessGuard;

    @PostMapping
    public ResponseEntity<Map<String, String>> createBlock(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody CreateBarberTimeBlockRequest request,
            @RequestParam(value = "branchId", required = false) Long branchIdParam
    ) {
        Map<String, Object> claims = getClaims(authHeader);
        Long tenantId = ((Number) claims.get("tenantId")).longValue();
        Long branchId = resolveBranchId(claims, branchIdParam);
        Long actorUserId = ((Number) claims.get("userId")).longValue();

        ownerBarberTimeBlockService.createBlock(tenantId, branchId, actorUserId, request);

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
        Long actorUserId = ((Number) claims.get("userId")).longValue();

        return ResponseEntity.ok(
                ownerBarberTimeBlockService.listBlocks(tenantId, branchId, barberUserId, actorUserId)
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
        Long actorUserId = ((Number) claims.get("userId")).longValue();

        ownerBarberTimeBlockService.deleteBlock(tenantId, branchId, actorUserId, blockId);

        return ResponseEntity.ok(Map.of("message", "Bloqueo eliminado correctamente"));
    }

    private Long resolveBranchId(Map<String, Object> claims, Long branchIdParam) {
        Object value = claims.get("branchId");
        Long sessionBranchId = value instanceof Number ? ((Number) value).longValue() : null;
        return branchAccessGuard.resolve(branchIdParam, sessionBranchId);
    }

    private Map<String, Object> getClaims(String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        return jwtService.extractAllClaims(token);
    }
}
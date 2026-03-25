package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.response.OwnerAgendaResponse;
import com.gods.saas.service.impl.JwtService;
import com.gods.saas.service.impl.impl.OwnerAgendaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/owner/agenda")
@RequiredArgsConstructor
public class OwnerAgendaController {

    private final OwnerAgendaService ownerAgendaService;
    private final JwtService jwtService;

    @GetMapping
    public List<OwnerAgendaResponse> getAgendaDelDia(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) String fecha
    ) {
        final String token = authHeader.replace("Bearer ", "");
        final Map<String, Object> claims = jwtService.extractAllClaims(token);

        final Long tenantId = ((Number) claims.get("tenantId")).longValue();
        final Object branchObj = claims.get("branchId");
        final Long branchId = branchObj != null ? ((Number) branchObj).longValue() : null;

        final LocalDate fechaConsulta = (fecha == null || fecha.isBlank())
                ? LocalDate.now()
                : LocalDate.parse(fecha);

        if (branchId == null) {
            throw new RuntimeException("No se encontró branchId en el token.");
        }

        return ownerAgendaService.getAgendaDelDia(tenantId, branchId, fechaConsulta);
    }
}
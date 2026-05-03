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
            @RequestParam(required = false) String fecha,
            @RequestParam(required = false) Long branchId
    ) {
        final String token = authHeader.replace("Bearer ", "");
        final Map<String, Object> claims = jwtService.extractAllClaims(token);

        final Long tenantId = ((Number) claims.get("tenantId")).longValue();

        Long branchIdFinal = branchId;

        if (branchIdFinal == null) {
            final Object branchObj = claims.get("branchId");
            branchIdFinal = branchObj != null ? ((Number) branchObj).longValue() : null;
        }

        final LocalDate fechaConsulta = (fecha == null || fecha.isBlank())
                ? LocalDate.now()
                : LocalDate.parse(fecha);

        if (branchIdFinal == null) {
            throw new RuntimeException("No se encontró branchId en el token ni en la consulta.");
        }

        return ownerAgendaService.getAgendaDelDia(tenantId, branchIdFinal, fechaConsulta);
    }
}
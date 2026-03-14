package com.gods.saas.web.controller;


import com.gods.saas.domain.dto.response.BarberAgendaItemResponse;
import com.gods.saas.service.impl.impl.BarberAgendaService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/barber")
@RequiredArgsConstructor
public class BarberAgendaController {

    private final BarberAgendaService barberAgendaService;

    @GetMapping("/agenda")
    public Map<String, Object> getAgenda(
            Authentication authentication,
            @RequestParam("fecha") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha
    ) {
        Map<String, Object> claims = getClaims(authentication);

        Long tenantId = toLong(claims.get("tenantId"));
        Long branchId = toLong(claims.get("branchId"));
        Long userId = toLong(claims.get("userId"));

        System.out.println("AUTH PRINCIPAL => " + authentication.getPrincipal());
        System.out.println("AUTH DETAILS   => " + authentication.getDetails());
        System.out.println("AUTH NAME      => " + authentication.getName());

        List<BarberAgendaItemResponse> items = barberAgendaService.getAgenda(
                tenantId,
                branchId,
                userId,
                fecha
        );

        return Map.of(
                "fecha", fecha.toString(),
                "items", items
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getClaims(Authentication authentication) {
        if (authentication == null || authentication.getDetails() == null) {
            throw new RuntimeException("No se pudo obtener la sesión autenticada");
        }

        if (authentication.getDetails() instanceof Map<?, ?> detailsMap) {
            return (Map<String, Object>) detailsMap;
        }

        throw new RuntimeException("No se pudieron leer los claims del token");
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        return Long.parseLong(value.toString());
    }
}
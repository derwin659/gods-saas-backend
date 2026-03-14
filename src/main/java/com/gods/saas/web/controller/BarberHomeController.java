package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.response.BarberCommissionResponse;
import com.gods.saas.domain.dto.response.BarberHomeResponse;
import com.gods.saas.service.impl.BarberCommissionService;
import com.gods.saas.service.impl.impl.BarberHomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/barber")
@RequiredArgsConstructor
public class BarberHomeController {

    private final BarberHomeService barberHomeService;
    private final BarberCommissionService barberCommissionService;

    @GetMapping("/home")
    public BarberHomeResponse getHome(Authentication authentication) {
        System.out.println("llego barber");
        return barberHomeService.getBarberHome(authentication);
    }

    @GetMapping("/commissions")
    public ResponseEntity<BarberCommissionResponse> getBarberCommissions(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to
    ) {
        return ResponseEntity.ok(barberCommissionService.getCommissions(from, to));
    }
}

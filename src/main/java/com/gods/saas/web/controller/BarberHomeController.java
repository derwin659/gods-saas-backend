package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.response.BarberCommissionResponse;
import com.gods.saas.domain.dto.response.BarberHomeResponse;
import com.gods.saas.service.impl.BarberCommissionService;
import com.gods.saas.service.impl.impl.BarberHomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/barber")
@RequiredArgsConstructor
public class BarberHomeController {

    private final BarberHomeService barberHomeService;
    private final BarberCommissionService barberCommissionService;

    @GetMapping("/home")
    public BarberHomeResponse getHome(Authentication authentication) {
        return barberHomeService.getBarberHome(authentication);
    }

    @PostMapping(value = "/me/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BarberHomeResponse uploadMyPhoto(
            Authentication authentication,
            @RequestParam("file") MultipartFile file
    ) {
        return barberHomeService.uploadMyPhoto(authentication, file);
    }

    @DeleteMapping("/me/photo")
    public BarberHomeResponse deleteMyPhoto(Authentication authentication) {
        return barberHomeService.deleteMyPhoto(authentication);
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
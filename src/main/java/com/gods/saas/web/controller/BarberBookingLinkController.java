package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.response.BarberBookingLinkResponse;
import com.gods.saas.service.impl.BarberBookingLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/barber")
@RequiredArgsConstructor
public class BarberBookingLinkController {

    private final BarberBookingLinkService barberBookingLinkService;

    @GetMapping("/booking-link")
    public ResponseEntity<BarberBookingLinkResponse> getBookingLink() {
        return ResponseEntity.ok(barberBookingLinkService.getBookingLink());
    }
}

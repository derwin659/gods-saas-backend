package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.response.OwnerBookingLinksResponse;
import com.gods.saas.service.impl.OwnerBookingLinksService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/owner/booking-links")
@RequiredArgsConstructor
public class OwnerBookingLinksController {

    private final OwnerBookingLinksService ownerBookingLinksService;

    @GetMapping
    public ResponseEntity<OwnerBookingLinksResponse> getOwnerBookingLinks() {
        return ResponseEntity.ok(ownerBookingLinksService.getOwnerBookingLinks());
    }
}

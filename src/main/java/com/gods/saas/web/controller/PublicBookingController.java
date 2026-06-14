package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.PublicCreateAppointmentRequest;
import com.gods.saas.domain.dto.response.BookingAvailabilityResponse;
import com.gods.saas.domain.dto.response.BookingBootstrapResponse;
import com.gods.saas.domain.dto.response.CreateAppointmentResponse;
import com.gods.saas.domain.dto.response.PublicBookingLinkInfoResponse;
import com.gods.saas.service.impl.ClientBookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/public/booking")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PublicBookingController {

    private final ClientBookingService clientBookingService;

    @GetMapping("/{codigoNegocio}/bootstrap")
    public ResponseEntity<BookingBootstrapResponse> bootstrap(
            @PathVariable String codigoNegocio,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long barberId
    ) {
        return ResponseEntity.ok(
                clientBookingService.getPublicBootstrapByCode(codigoNegocio, branchId, barberId)
        );
    }

    @GetMapping("/{codigoNegocio}/link-info")
    public ResponseEntity<PublicBookingLinkInfoResponse> linkInfo(
            @PathVariable String codigoNegocio,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long barberId
    ) {
        return ResponseEntity.ok(
                clientBookingService.getPublicBookingLinkInfo(codigoNegocio, branchId, barberId)
        );
    }

    @GetMapping("/{codigoNegocio}/availability")
    public ResponseEntity<BookingAvailabilityResponse> availability(
            @PathVariable String codigoNegocio,
            @RequestParam Long branchId,
            @RequestParam Long serviceId,
            @RequestParam String date,
            @RequestParam(required = false) List<Long> serviceIds,
            @RequestParam(required = false) Long barberId
    ) {
        return ResponseEntity.ok(
                clientBookingService.getPublicAvailabilityByCode(
                        codigoNegocio,
                        branchId,
                        serviceId,
                        serviceIds,
                        date,
                        barberId
                )
        );
    }

    @PostMapping("/{codigoNegocio}/appointments")
    public ResponseEntity<CreateAppointmentResponse> createAppointment(
            @PathVariable String codigoNegocio,
            @RequestBody PublicCreateAppointmentRequest request
    ) {
        return ResponseEntity.ok(
                clientBookingService.createPublicAppointment(codigoNegocio, request)
        );
    }
}

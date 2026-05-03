package com.gods.saas.controller.owner;

import com.gods.saas.domain.dto.request.UpdateBookingPaymentSettingsRequest;
import com.gods.saas.domain.dto.response.BookingPaymentSettingsResponse;
import com.gods.saas.service.impl.OwnerBookingPaymentSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/owner/booking-payment-settings")
@RequiredArgsConstructor
public class OwnerBookingPaymentSettingsController {

    private final OwnerBookingPaymentSettingsService service;

    @GetMapping
    public BookingPaymentSettingsResponse getSettings(HttpServletRequest request) {
        Long tenantId = resolveTenantId(request);
        return service.getSettings(tenantId);
    }

    @PutMapping
    public BookingPaymentSettingsResponse updateSettings(
            HttpServletRequest request,
            @RequestBody UpdateBookingPaymentSettingsRequest body
    ) {
        Long tenantId = resolveTenantId(request);
        return service.updateSettings(tenantId, body);
    }

    private Long resolveTenantId(HttpServletRequest request) {
        Object value = request.getAttribute("tenantId");

        if (value == null) {
            value = request.getAttribute("tenant_id");
        }

        if (value == null) {
            throw new RuntimeException("No se pudo resolver tenantId desde la sesión");
        }

        if (value instanceof Number n) {
            return n.longValue();
        }

        return Long.parseLong(value.toString());
    }
}
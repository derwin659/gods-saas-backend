package com.gods.saas.web.controller;

import com.gods.saas.domain.repository.TenantRepository;
import com.gods.saas.service.impl.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

/** Phone verification reuses the client login protections; never trusts a public customer id. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/public/booking")
public class PublicWaitlistAccessController {
    private final TenantRepository tenants;
    private final CustomerService customers;
    @Value("${booking.waitlist.enabled:false}") private boolean enabled;
    public record AccessRequest(String phone, String locale) {}

    @PostMapping("/{code}/waitlist/access")
    public Map<String, Object> access(@PathVariable String code, @RequestBody AccessRequest request) {
        if (!enabled) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "La lista de espera aun no esta habilitada.");
        var tenant = tenants.findByCodigoIgnoreCaseAndActiveTrue(code.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Negocio no disponible."));
        if (request.phone() == null || request.phone().length() > 30)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ingresa tu celular con codigo de pais.");
        var dispatch = customers.requestLoginOtp(tenant.getId(), request.phone(), request.locale(), null);
        return Map.of("tenantId", tenant.getId(), "otpId", dispatch.otpId(), "ttl", dispatch.ttl(), "channel", dispatch.channel());
    }
    public record RegistrationRequest(String phone, String locale, String firstName, String lastName, Boolean acceptedTerms) {}

    @PostMapping("/{code}/waitlist/register")
    public Map<String, Object> register(@PathVariable String code, @RequestBody RegistrationRequest request) {
        if (!enabled) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "La lista de espera aun no esta habilitada.");
        var tenant = tenants.findByCodigoIgnoreCaseAndActiveTrue(code.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Negocio no disponible."));
        if (!Boolean.TRUE.equals(request.acceptedTerms()) || request.firstName() == null || request.firstName().isBlank()
                || request.firstName().length() > 100 || (request.lastName() != null && request.lastName().length() > 100)
                || request.phone() == null || request.phone().length() > 30)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Completa tu nombre, celular y acepta las condiciones.");
        // Existing accounts are rejected by registerFromApp; never overwrite another customer's details.
        customers.registerFromApp(tenant.getId(), request.phone(), null, request.firstName().trim(), request.lastName(), request.locale());
        return Map.of("created", true);
    }}

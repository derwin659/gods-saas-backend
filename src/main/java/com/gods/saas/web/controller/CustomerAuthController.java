package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.ClientRegisterRequest;
import com.gods.saas.domain.dto.request.OtpRequest;
import com.gods.saas.domain.dto.request.OtpVerifyRequest;
import com.gods.saas.domain.dto.response.ClientLoginResponse;
import com.gods.saas.domain.model.Customer;
import com.gods.saas.service.impl.CustomerService;
import com.gods.saas.service.impl.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth/cliente")
@RequiredArgsConstructor
public class CustomerAuthController {

    private final CustomerService customerService;
    private final JwtService jwtService; // o tu servicio que genera tokens

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody ClientRegisterRequest req) {
        Customer c = customerService.registerFromApp(
                req.getTenantId(),
                req.getPhone(),
                req.getNombres(),
                req.getApellidos(),
                req.getLocale()
        );
        return ResponseEntity.ok(Map.of("customerId", c.getId(), "created", true));
    }

    @PostMapping("/otp/request")
    public ResponseEntity<?> requestOtp(@RequestBody OtpRequest req) {
        CustomerService.OtpDispatch dispatch = customerService.requestLoginOtp(
                req.getTenantId(), req.getPhone(), req.getLocale()
        );
        return ResponseEntity.ok(Map.of(
                "otpId", dispatch.otpId(),
                "ttl", dispatch.ttl(),
                "channel", dispatch.channel()
        ));
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<?> verifyOtp(@RequestBody OtpVerifyRequest req) {
        ClientLoginResponse login = customerService.verifyLoginOtp(req.getOtpId(), req.getCode());

        Long tenantId = login.getTenantId();
        Long customerId = login.getCustomerId();

        String token = jwtService.generateCustomerToken(customerId, tenantId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("token", token);
        response.put("role", "CLIENT");
        response.put("tenantId", tenantId);
        response.put("tenantName", login.getTenantNombre() == null ? "" : login.getTenantNombre());
        response.put("tenantLogoUrl", login.getTenantLogoUrl() == null ? "" : login.getTenantLogoUrl());
        response.put("customerId", customerId);
        response.put("phoneVerified", login.getPhoneVerified());
        response.put("appActivated", login.getAppActivated());
        response.put("locale", login.getLocale());
        response.put("tenantLocale", login.getTenantLocale());
        response.put("timezone", login.getTimezone());
        response.put("currency", login.getCurrency());
        response.put("country", login.getCountry());
        return ResponseEntity.ok(response);
    }
}
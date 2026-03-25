package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.ClientRegisterRequest;
import com.gods.saas.domain.dto.request.OtpRequest;
import com.gods.saas.domain.dto.request.OtpVerifyRequest;
import com.gods.saas.domain.dto.response.ClientLoginResponse;
import com.gods.saas.domain.model.Customer;
import com.gods.saas.domain.model.OtpCode;
import com.gods.saas.service.impl.CustomerService;
import com.gods.saas.service.impl.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth/cliente")
@RequiredArgsConstructor
public class CustomerAuthController {

    private final CustomerService customerService;
    private final JwtService jwtService; // o tu servicio que genera tokens

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody ClientRegisterRequest req) {
        Customer c = customerService.registerFromApp(req.getTenantId(), req.getPhone(), req.getNombres(), req.getApellidos());
        return ResponseEntity.ok(Map.of("customerId", c.getId(), "created", true));
    }

    @PostMapping("/otp/request")
    public ResponseEntity<?> requestOtp(@RequestBody OtpRequest req) {
        OtpCode otp = customerService.requestLoginOtp(req.getTenantId(), req.getPhone());
        return ResponseEntity.ok(Map.of(
                "otpId", otp.getId(),
                "ttl", 300,
                "devCode", otp.getCode() // SOLO DEV
        ));
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<?> verifyOtp(@RequestBody OtpVerifyRequest req) {
        ClientLoginResponse login = customerService.verifyLoginOtp(req.getOtpId(), req.getCode());

        Long tenantId = login.getTenantId();
        Long customerId = login.getCustomerId();

        String token = jwtService.generateCustomerToken(customerId, tenantId);

        return ResponseEntity.ok(Map.of(
                "token", token,
                "role", "CLIENT",
                "tenantId", tenantId,
                "tenantName", login.getTenantNombre() == null ? "" : login.getTenantNombre(),
                "tenantLogoUrl", login.getTenantLogoUrl(),
                "customerId", customerId,
                "phoneVerified", login.getPhoneVerified(),
                "appActivated", login.getAppActivated()
        ));
    }
}
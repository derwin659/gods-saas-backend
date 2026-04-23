package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.DeleteMyAccountRequest;
import com.gods.saas.domain.dto.response.DeleteAccountResponse;
import com.gods.saas.service.impl.CustomerService;
import com.gods.saas.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
public class ClientAccountController {

    private final CustomerService customerService;
    private final JwtUtil jwtUtil;

    @DeleteMapping("/me")
    public ResponseEntity<DeleteAccountResponse> deleteMyAccount(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody DeleteMyAccountRequest request
    ) {
        String token = authHeader.replace("Bearer ", "");
        Long customerId = jwtUtil.getUserIdFromToken(token);

        customerService.deleteMyCustomerAccount(
                customerId,
                request == null ? null : request.getConfirmation()
        );

        return ResponseEntity.ok(
                DeleteAccountResponse.builder()
                        .success(true)
                        .message("Tu cuenta fue eliminada correctamente")
                        .build()
        );
    }
}
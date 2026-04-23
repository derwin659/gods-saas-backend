package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.DeleteMyAccountRequest;
import com.gods.saas.domain.dto.response.DeleteAccountResponse;
import com.gods.saas.service.impl.CustomerService;
import com.gods.saas.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
public class ClientAccountController {

    private final CustomerService customerService;
    private final JwtUtil jwtUtil;

    @DeleteMapping("/delete")
    public ResponseEntity<DeleteAccountResponse> deleteMyAccount(
            @RequestBody DeleteMyAccountRequest request
    ) {
        if (request == null || request.getCustomerId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cliente inválido");
        }

        customerService.deleteMyCustomerAccount(
                request.getCustomerId(),
                request.getConfirmation()
        );

        return ResponseEntity.ok(
                DeleteAccountResponse.builder()
                        .success(true)
                        .message("Tu cuenta fue eliminada correctamente")
                        .build()
        );
    }
}
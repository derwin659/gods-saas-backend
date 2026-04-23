package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.ChangePasswordRequest;
import com.gods.saas.domain.dto.request.DeleteMyAccountRequest;
import com.gods.saas.domain.dto.response.DeleteAccountResponse;
import com.gods.saas.domain.model.AppUser;
import com.gods.saas.service.impl.CustomerService;
import com.gods.saas.service.impl.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/internal/me")
@RequiredArgsConstructor
public class AccountController {

    private final UserService userService;
    private final CustomerService customerService;

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(
            Authentication authentication,
            @RequestBody ChangePasswordRequest request
    ) {
        Long userId = extractUserId(authentication);

        userService.changeMyPassword(userId, request);

        return ResponseEntity.ok(
                Map.of("message", "Contraseña actualizada correctamente")
        );
    }

    @PostMapping("/me/delete")
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

    private Long extractUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new RuntimeException("Sesión no válida");
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof Number userIdNum) {
            return userIdNum.longValue();
        }

        if (principal instanceof AppUser appUser) {
            return appUser.getId();
        }

        throw new RuntimeException("No se pudo identificar al usuario autenticado");
    }
}
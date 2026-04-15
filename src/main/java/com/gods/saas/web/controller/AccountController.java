package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.ChangePasswordRequest;
import com.gods.saas.service.impl.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/internal/me")
@RequiredArgsConstructor
public class AccountController {

    private final UserService userService;

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(
            Authentication authentication,
            @RequestBody ChangePasswordRequest request
    ) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(401).body(
                    Map.of("message", "Sesión no válida")
            );
        }

        Object principal = authentication.getPrincipal();

        if (!(principal instanceof Number userIdNum)) {
            return ResponseEntity.status(401).body(
                    Map.of("message", "No se pudo identificar al usuario autenticado")
            );
        }

        Long userId = userIdNum.longValue();

        userService.changeMyPassword(userId, request);

        return ResponseEntity.ok(
                Map.of("message", "Contraseña actualizada correctamente")
        );
    }
}
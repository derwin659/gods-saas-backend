package com.gods.saas.web.controller;


import com.gods.saas.domain.model.AppUser;
import com.gods.saas.domain.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
public class InternalAuthController {

    private final AppUserRepository appUserRepository;

    @GetMapping("/me")
    public ResponseEntity<?> meInternal(Authentication authentication) {

        Long userId = (Long) authentication.getPrincipal();

        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        String role = authentication.getAuthorities()
                .stream()
                .findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .orElse("UNKNOWN");

        return ResponseEntity.ok(
                Map.of(
                        "userId", user.getId(),
                        "nombre", user.getNombre(),
                        "email", user.getEmail(),
                        "role", role,
                        "tenantId", user.getTenant().getId(),
                        "tenantName", user.getTenant().getNombre()
                )
        );
    }
}

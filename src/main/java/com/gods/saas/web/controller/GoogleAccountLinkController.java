package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.GoogleOAuthStartResponse;
import com.gods.saas.domain.model.AppUser;
import com.gods.saas.domain.repository.AppUserRepository;
import com.gods.saas.service.impl.GoogleOAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("/api/internal/me/google-link")
@RequiredArgsConstructor
public class GoogleAccountLinkController {

    private final GoogleOAuthService googleOAuthService;
    private final AppUserRepository appUserRepository;

    @PostMapping("/start")
    public ResponseEntity<GoogleOAuthStartResponse> startGoogleLink(
            Authentication authentication,
            @RequestParam(required = false) String redirectUri
    ) {
        AppUser user = resolveAuthenticatedUser(authentication);
        return ResponseEntity.ok(new GoogleOAuthStartResponse(
                googleOAuthService.buildLinkUrl(user, redirectUri)
        ));
    }

    private AppUser resolveAuthenticatedUser(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "Usuario no autenticado");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof AppUser user) {
            return user;
        }

        if (principal instanceof Number number) {
            return appUserRepository.findById(number.longValue())
                    .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Usuario no autenticado"));
        }

        return appUserRepository.findByEmailIgnoreCase(String.valueOf(principal))
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Usuario no autenticado"));
    }
}

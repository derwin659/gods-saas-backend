package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.GoogleOAuthStartResponse;
import com.gods.saas.domain.model.AppUser;
import com.gods.saas.service.impl.GoogleOAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/internal/me/google-link")
@RequiredArgsConstructor
public class GoogleAccountLinkController {

    private final GoogleOAuthService googleOAuthService;

    @PostMapping("/start")
    public ResponseEntity<GoogleOAuthStartResponse> startGoogleLink(
            Authentication authentication,
            @RequestParam(required = false) String redirectUri
    ) {
        AppUser user = (AppUser) authentication.getPrincipal();
        return ResponseEntity.ok(new GoogleOAuthStartResponse(
                googleOAuthService.buildLinkUrl(user, redirectUri)
        ));
    }
}

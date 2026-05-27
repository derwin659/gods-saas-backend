package com.gods.saas.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gods.saas.domain.dto.LoginFinalResponse;
import com.gods.saas.domain.model.AppUser;
import com.gods.saas.domain.model.Branch;
import com.gods.saas.domain.model.Tenant;
import com.gods.saas.domain.model.UserTenantRole;
import com.gods.saas.domain.repository.AppUserRepository;
import com.gods.saas.domain.repository.UserTenantRoleRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
public class GoogleOAuthService {

    private static final String ACTION_LOGIN = "LOGIN";
    private static final String ACTION_LINK = "LINK";
    private static final String ACTION_SIGNUP = "SIGNUP";
    private static final String ACTION_SIGNUP_PROFILE = "SIGNUP_PROFILE";

    private final AppUserRepository appUserRepository;
    private final UserTenantRoleRepository userTenantRoleRepository;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${google.oauth.client-id:}")
    private String clientId;

    @Value("${google.oauth.client-secret:}")
    private String clientSecret;

    @Value("${google.oauth.callback-url:}")
    private String callbackUrl;

    @Value("${google.oauth.state-secret:${jwt.secret}}")
    private String stateSecret;

    @Value("${google.oauth.default-web-redirect:https://www.supergodsapp.com/auth/google/callback}")
    private String defaultWebRedirect;

    public URI buildLoginUrl(String mode, String redirectUri) {
        assertConfigured();
        String cleanMode = cleanMode(mode);
        Map<String, Object> state = new HashMap<>();
        state.put("action", "SIGNUP".equals(cleanMode) ? ACTION_SIGNUP : ACTION_LOGIN);
        state.put("mode", cleanMode);
        state.put("redirectUri", cleanRedirectUri(redirectUri));
        state.put("createdAt", Instant.now().getEpochSecond());
        return buildGoogleUrl(signState(state));
    }

    public String buildLinkUrl(AppUser user, String redirectUri) {
        assertConfigured();
        if (user == null || user.getId() == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "Usuario no autenticado");
        }

        Map<String, Object> state = new HashMap<>();
        state.put("action", ACTION_LINK);
        state.put("userId", user.getId());
        state.put("redirectUri", cleanRedirectUri(redirectUri));
        state.put("createdAt", Instant.now().getEpochSecond());
        return buildGoogleUrl(signState(state)).toString();
    }

    @Transactional
    public URI handleCallback(String code, String stateToken) {
        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Google no devolvio codigo de autorizacion");
        }

        Map<String, Object> state = verifyState(stateToken);
        GoogleProfile profile = exchangeCodeForProfile(code);
        String action = String.valueOf(state.getOrDefault("action", ""));
        String redirectUri = cleanRedirectUri((String) state.get("redirectUri"));

        if (ACTION_LINK.equals(action)) {
            Long userId = asLong(state.get("userId"));
            linkGoogleAccount(userId, profile);
            return UriComponentsBuilder.fromUriString(redirectUri)
                    .queryParam("googleLinked", "1")
                    .build(true)
                    .toUri();
        }

        if (ACTION_SIGNUP.equals(action)) {
            return redirectWithSignupProfile(redirectUri, profile);
        }

        LoginFinalResponse session = loginWithGoogle(profile);
        return redirectWithSession(redirectUri, session);
    }

    public GoogleSignupProfile verifySignupToken(String token) {
        Map<String, Object> state = verifyState(token);
        String action = String.valueOf(state.getOrDefault("action", ""));
        if (!ACTION_SIGNUP_PROFILE.equals(action)) {
            throw new ResponseStatusException(UNAUTHORIZED, "Token de registro Google invalido");
        }

        String subject = String.valueOf(state.getOrDefault("subject", "")).trim();
        String email = String.valueOf(state.getOrDefault("email", "")).trim();
        String name = String.valueOf(state.getOrDefault("name", "")).trim();
        String pictureUrl = String.valueOf(state.getOrDefault("pictureUrl", "")).trim();

        if (subject.isEmpty() || email.isEmpty()) {
            throw new ResponseStatusException(UNAUTHORIZED, "Token de registro Google incompleto");
        }

        return new GoogleSignupProfile(subject, email, name, pictureUrl);
    }

    private URI buildGoogleUrl(String stateToken) {
        return UriComponentsBuilder
                .fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", callbackUrl)
                .queryParam("response_type", "code")
                .queryParam("scope", "openid email profile")
                .queryParam("state", stateToken)
                .queryParam("access_type", "offline")
                .queryParam("prompt", "select_account")
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUri();
    }

    private LoginFinalResponse loginWithGoogle(GoogleProfile profile) {
        AppUser user = appUserRepository.findByGoogleSubject(profile.subject())
                .or(() -> appUserRepository.findByEmailIgnoreCase(profile.email()))
                .orElseThrow(() -> new ResponseStatusException(
                        UNAUTHORIZED,
                        "No existe una cuenta interna vinculada a este Gmail"
                ));

        ensureGoogleLinked(user, profile);
        List<UserTenantRole> roles = userTenantRoleRepository.findByUserIdWithRelations(user.getId());

        UserTenantRole selected = roles.stream()
                .filter(role -> role.getBranch() != null)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        UNAUTHORIZED,
                        "Tu cuenta no tiene sede asignada para entrar con Google"
                ));

        Tenant tenant = selected.getTenant();
        Branch branch = selected.getBranch();
        String role = selected.getRole().name();

        String token = jwtService.generateToken(user, tenant.getId(), role, branch.getId());
        return LoginFinalResponse.builder()
                .token(token)
                .userId(user.getId())
                .nombre(user.getNombre())
                .email(user.getEmail())
                .tenantId(tenant.getId())
                .tenantName(tenant.getNombre())
                .businessType(
                        tenant.getBusinessType() != null && !tenant.getBusinessType().isBlank()
                                ? tenant.getBusinessType().trim().toUpperCase()
                                : "BARBERSHOP"
                )
                .branchId(branch.getId())
                .branchName(branch.getNombre())
                .role(role)
                .build();
    }

    private void linkGoogleAccount(Long userId, GoogleProfile profile) {
        AppUser existing = appUserRepository.findByGoogleSubject(profile.subject()).orElse(null);
        if (existing != null && !existing.getId().equals(userId)) {
            throw new ResponseStatusException(CONFLICT, "Este Gmail ya esta vinculado a otro usuario");
        }

        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Usuario no encontrado"));

        ensureGoogleLinked(user, profile);
    }

    private void ensureGoogleLinked(AppUser user, GoogleProfile profile) {
        user.setGoogleSubject(profile.subject());
        user.setGoogleEmail(profile.email());
        user.setGoogleName(profile.name());
        user.setGooglePictureUrl(profile.pictureUrl());
        user.setGoogleLinkedAt(LocalDateTime.now());
        appUserRepository.save(user);
    }

    private URI redirectWithSession(String redirectUri, LoginFinalResponse session) {
        try {
            String json = objectMapper.writeValueAsString(session);
            String encoded = base64Url(json.getBytes(StandardCharsets.UTF_8));
            return UriComponentsBuilder.fromUriString(redirectUri)
                    .queryParam("session", encoded)
                    .build(true)
                    .toUri();
        } catch (Exception e) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "No se pudo crear la sesion Google");
        }
    }

    private URI redirectWithSignupProfile(String redirectUri, GoogleProfile profile) {
        Map<String, Object> signupState = new HashMap<>();
        signupState.put("action", ACTION_SIGNUP_PROFILE);
        signupState.put("subject", profile.subject());
        signupState.put("email", profile.email());
        signupState.put("name", profile.name());
        signupState.put("pictureUrl", profile.pictureUrl());
        signupState.put("createdAt", Instant.now().getEpochSecond());

        String signupToken = signState(signupState);
        return UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("googleSignup", "1")
                .queryParam("googleSignupToken", signupToken)
                .queryParam("googleEmail", profile.email())
                .queryParam("googleName", profile.name())
                .queryParam("googlePicture", profile.pictureUrl())
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUri();
    }

    private GoogleProfile exchangeCodeForProfile(String code) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("code", code);
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("redirect_uri", callbackUrl);
        body.add("grant_type", "authorization_code");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        Map<String, Object> tokenResponse = restTemplate.postForObject(
                "https://oauth2.googleapis.com/token",
                new HttpEntity<>(body, headers),
                Map.class
        );

        if (tokenResponse == null || tokenResponse.get("id_token") == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "Google no devolvio id_token");
        }

        return parseGoogleIdToken(String.valueOf(tokenResponse.get("id_token")));
    }

    private GoogleProfile parseGoogleIdToken(String idToken) {
        try {
            String[] parts = idToken.split("\\.");
            if (parts.length < 2) {
                throw new IllegalArgumentException("Token invalido");
            }

            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> payload = objectMapper.readValue(
                    payloadBytes,
                    new TypeReference<Map<String, Object>>() {}
            );

            if (!clientId.equals(String.valueOf(payload.get("aud")))) {
                throw new ResponseStatusException(UNAUTHORIZED, "Token Google con audience invalido");
            }

            Long exp = asLong(payload.get("exp"));
            if (exp == null || exp < Instant.now().getEpochSecond()) {
                throw new ResponseStatusException(UNAUTHORIZED, "Token Google expirado");
            }

            String subject = String.valueOf(payload.getOrDefault("sub", "")).trim();
            String email = String.valueOf(payload.getOrDefault("email", "")).trim();
            String name = String.valueOf(payload.getOrDefault("name", "")).trim();
            String pictureUrl = String.valueOf(payload.getOrDefault("picture", "")).trim();
            boolean emailVerified = Boolean.parseBoolean(String.valueOf(payload.getOrDefault("email_verified", "false")));

            if (subject.isEmpty() || email.isEmpty() || !emailVerified) {
                throw new ResponseStatusException(UNAUTHORIZED, "Google no confirmo un correo valido");
            }

            return new GoogleProfile(subject, email, name, pictureUrl);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(UNAUTHORIZED, "No se pudo validar el token de Google");
        }
    }

    private String signState(Map<String, Object> state) {
        try {
            String payload = base64Url(objectMapper.writeValueAsBytes(state));
            String signature = base64Url(hmac(payload));
            return payload + "." + signature;
        } catch (Exception e) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "No se pudo crear state OAuth");
        }
    }

    private Map<String, Object> verifyState(String token) {
        try {
            if (token == null || !token.contains(".")) {
                throw new IllegalArgumentException("State vacio");
            }

            String[] parts = token.split("\\.", 2);
            String expected = base64Url(hmac(parts[0]));
            if (!constantTimeEquals(expected, parts[1])) {
                throw new ResponseStatusException(UNAUTHORIZED, "State OAuth invalido");
            }

            byte[] payload = Base64.getUrlDecoder().decode(parts[0]);
            Map<String, Object> state = objectMapper.readValue(
                    payload,
                    new TypeReference<Map<String, Object>>() {}
            );

            Long createdAt = asLong(state.get("createdAt"));
            if (createdAt == null || createdAt < Instant.now().minusSeconds(900).getEpochSecond()) {
                throw new ResponseStatusException(UNAUTHORIZED, "State OAuth expirado");
            }

            return state;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(UNAUTHORIZED, "State OAuth invalido");
        }
    }

    private byte[] hmac(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(stateSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) return false;
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null) return null;
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String cleanMode(String mode) {
        String value = mode == null ? "OWNER" : mode.trim().toUpperCase();
        return value.isBlank() ? "OWNER" : value;
    }

    private String cleanRedirectUri(String redirectUri) {
        if (redirectUri == null || redirectUri.isBlank()) return defaultWebRedirect;
        String value = redirectUri.trim();
        if (!value.startsWith("https://www.supergodsapp.com")
                && !value.startsWith("https://supergodsapp.com")
                && !value.startsWith("http://localhost:5173")) {
            return defaultWebRedirect;
        }
        return value;
    }

    private void assertConfigured() {
        if (clientId == null || clientId.isBlank()
                || clientSecret == null || clientSecret.isBlank()
                || callbackUrl == null || callbackUrl.isBlank()) {
            throw new ResponseStatusException(
                    SERVICE_UNAVAILABLE,
                    "Google OAuth no esta configurado en el backend"
            );
        }
    }

    public record GoogleSignupProfile(String subject, String email, String name, String pictureUrl) {}

    private record GoogleProfile(String subject, String email, String name, String pictureUrl) {}
}

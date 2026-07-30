package com.gods.saas.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gods.saas.domain.model.Tenant;
import com.gods.saas.utils.RegionalDefaults;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TwilioVerifyOtpService {

    @Value("${twilio.verify.enabled:false}")
    private boolean enabled;

    @Value("${twilio.verify.service-sid:}")
    private String serviceSid;

    @Value("${twilio.verify.channel:sms}")
    private String configuredChannel;

    @Value("${twilio.verify.api-key:}")
    private String apiKey;

    @Value("${twilio.verify.api-secret:}")
    private String apiSecret;

    @Value("${twilio.verify.ttl-seconds:600}")
    private int configuredTtlSeconds;

    @Value("${twilio.verify.resend-cooldown-seconds:60}")
    private int configuredResendCooldownSeconds;

    @Value("${whatsapp.twilio.account-sid:}")
    private String accountSid;

    @Value("${whatsapp.twilio.auth-token:}")
    private String authToken;

    private final ObjectMapper objectMapper;
    private final InternationalPhoneService internationalPhoneService;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public void sendCode(Tenant tenant, String rawPhone, String locale) {
        requireConfigured();
        String phone = normalizePhone(tenant, rawPhone);
        Map<String, String> form = new LinkedHashMap<>();
        form.put("To", phone);
        form.put("Channel", channel());
        form.put("Locale", RegionalDefaults.normalizeLocale(locale, tenant == null ? null : tenant.getPais()));

        JsonNode response = post("/Verifications", form, false);
        if (!"pending".equalsIgnoreCase(response.path("status").asText())) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Twilio no pudo iniciar la verificacion."
            );
        }
    }

    public boolean checkCode(Tenant tenant, String rawPhone, String code) {
        requireConfigured();
        String phone = normalizePhone(tenant, rawPhone);
        Map<String, String> form = new LinkedHashMap<>();
        form.put("To", phone);
        form.put("Code", code);

        JsonNode response = post("/VerificationCheck", form, true);
        return "approved".equalsIgnoreCase(response.path("status").asText())
                && response.path("valid").asBoolean(false);
    }

    public String channel() {
        String value = clean(configuredChannel);
        if (value == null) return "sms";
        value = value.toLowerCase(Locale.ROOT);
        return switch (value) {
            case "sms", "whatsapp" -> value;
            default -> "sms";
        };
    }

    public int ttlSeconds() {
        return Math.max(120, Math.min(configuredTtlSeconds, 600));
    }

    public int resendCooldownSeconds() {
        return Math.max(30, Math.min(configuredResendCooldownSeconds, 300));
    }

    private JsonNode post(String resource, Map<String, String> form, boolean checkingCode) {
        try {
            String credentials = credentials();
            String body = form.entrySet().stream()
                    .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                    .reduce((left, right) -> left + "&" + right)
                    .orElse("");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(
                            "https://verify.twilio.com/v2/Services/"
                                    + encodePath(serviceSid.trim())
                                    + resource
                    ))
                    .header("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                            credentials.getBytes(StandardCharsets.UTF_8)
                    ))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return objectMapper.readTree(response.body());
            }

            if (response.statusCode() == 429) {
                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Demasiados intentos. Espera unos minutos."
                );
            }
            if (checkingCode && (response.statusCode() == 400 || response.statusCode() == 404)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Codigo incorrecto, vencido o con demasiados intentos."
                );
            }
            if (!checkingCode && response.statusCode() == 400) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "El numero no puede recibir el codigo de verificacion."
                );
            }

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "El servicio de codigos no esta disponible temporalmente."
            );
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "La verificacion fue interrumpida."
            );
        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo conectar con el servicio de codigos."
            );
        }
    }

    private void requireConfigured() {
        if (!enabled
                || clean(serviceSid) == null
                || (!hasApiKeyCredentials() && !hasAccountCredentials())) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "El ingreso seguro por telefono aun no esta habilitado."
            );
        }
    }

    private String credentials() {
        if (hasApiKeyCredentials()) {
            return apiKey.trim() + ":" + apiSecret.trim();
        }
        return accountSid.trim() + ":" + authToken.trim();
    }

    private boolean hasApiKeyCredentials() {
        return clean(apiKey) != null && clean(apiSecret) != null;
    }

    private boolean hasAccountCredentials() {
        return clean(accountSid) != null && clean(authToken) != null;
    }

    private String normalizePhone(Tenant tenant, String rawPhone) {
        return internationalPhoneService.normalize(tenant, rawPhone).e164();
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String encodePath(String value) {
        return value.replaceAll("[^A-Za-z0-9]", "");
    }

    private String clean(String value) {
        if (value == null) return null;
        String result = value.trim();
        return result.isEmpty() ? null : result;
    }
}

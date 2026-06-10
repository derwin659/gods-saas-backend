package com.gods.saas.service.impl;

import com.gods.saas.domain.model.Customer;
import com.gods.saas.domain.model.Notification;
import com.gods.saas.domain.model.Tenant;
import com.gods.saas.domain.model.TenantSettings;
import com.gods.saas.domain.repository.TenantSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsappNotificationSenderMetaCloudImpl {

    private static final String PHONE_NUMBER_ID_KEY = "whatsappPhoneNumberId";
    private static final String ACCESS_TOKEN_KEY = "whatsappAccessToken";

    @Value("${whatsapp.meta.phone-number-id:}")
    private String defaultPhoneNumberId;

    @Value("${whatsapp.meta.access-token:}")
    private String defaultAccessToken;

    private final TenantSettingsRepository tenantSettingsRepository;

    @Override
    public String toString() {
        return "WhatsappNotificationSenderMetaCloudImpl";
    }

    public String send(Notification notification) {
        if (notification == null) {
            throw new IllegalArgumentException("La notificación no puede ser null");
        }

        if (notification.getTenant() == null || notification.getTenant().getId() == null) {
            throw new IllegalArgumentException("La notificación no tiene tenant válido");
        }

        Customer customer = notification.getCustomer();
        if (customer == null) {
            throw new RuntimeException("La notificación no tiene cliente para enviar WhatsApp");
        }

        String phone = normalizeWhatsappPhone(customer.getTelefono(), notification.getTenant());
        if (phone == null || phone.isBlank()) {
            throw new RuntimeException("El cliente no tiene teléfono válido para WhatsApp");
        }

        Map<String, Object> config = tenantSettingsRepository
                .findByTenant_Id(notification.getTenant().getId())
                .map(TenantSettings::getScheduleConfig)
                .orElse(Map.of());

        String phoneNumberId = readString(config, PHONE_NUMBER_ID_KEY, defaultPhoneNumberId);
        String accessToken = readString(config, ACCESS_TOKEN_KEY, defaultAccessToken);

        if (phoneNumberId == null || phoneNumberId.isBlank()) {
            throw new RuntimeException("Falta configurar whatsappPhoneNumberId");
        }

        if (accessToken == null || accessToken.isBlank()) {
            throw new RuntimeException("Falta configurar whatsappAccessToken");
        }

        String message = notification.getMessage();
        if (message == null || message.isBlank()) {
            throw new RuntimeException("La notificación no tiene mensaje");
        }

        try {
            String url = "https://graph.facebook.com/v20.0/" + phoneNumberId + "/messages";

            String body = """
                    {
                      "messaging_product": "whatsapp",
                      "to": "%s",
                      "type": "text",
                      "text": {
                        "preview_url": true,
                        "body": "%s"
                      }
                    }
                    """.formatted(
                    escapeJson(phone),
                    escapeJson(message)
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpClient client = HttpClient.newHttpClient();

            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error(
                        "META WHATSAPP FAILED => notificationId={}, status={}, body={}",
                        notification.getId(),
                        response.statusCode(),
                        response.body()
                );

                throw new RuntimeException("Meta WhatsApp error " + response.statusCode() + ": " + limit(response.body(), 300));
            }

            log.info(
                    "META WHATSAPP SENT => notificationId={}, customerId={}, phone={}",
                    notification.getId(),
                    customer.getId(),
                    phone
            );

            return extractMetaMessageId(response.body());

        } catch (Exception e) {
            throw new RuntimeException("No se pudo enviar WhatsApp Meta Cloud: " + e.getMessage(), e);
        }
    }

    private String normalizeWhatsappPhone(String rawPhone, Tenant tenant) {
        String digits = rawPhone == null ? "" : rawPhone.replaceAll("[^0-9]", "");

        if (digits.isBlank()) {
            return null;
        }

        if (digits.startsWith("00") && digits.length() > 2) {
            digits = digits.substring(2);
        }

        if (digits.length() >= 11) {
            return digits;
        }

        String country = tenant == null ? null : cleanText(tenant.getPais());
        String prefix = whatsappCountryPrefix(country);

        if (prefix == null) {
            return digits;
        }

        if (digits.startsWith(prefix)) {
            return digits;
        }

        return prefix + digits;
    }

    private String whatsappCountryPrefix(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return null;
        }

        return switch (countryCode.trim().toUpperCase(Locale.ROOT)) {
            case "PE", "PERU" -> "51";
            case "CO", "COLOMBIA" -> "57";
            case "MX", "MEXICO" -> "52";
            case "CL", "CHILE" -> "56";
            case "AR", "ARGENTINA" -> "54";
            case "BO", "BOLIVIA" -> "591";
            case "BR", "BRASIL", "BRAZIL" -> "55";
            case "UY", "URUGUAY" -> "598";
            case "PY", "PARAGUAY" -> "595";
            case "CR", "COSTA RICA" -> "506";
            case "DO", "REPUBLICA DOMINICANA", "DOMINICAN REPUBLIC" -> "1";
            case "GT", "GUATEMALA" -> "502";
            case "US", "USA", "UNITED STATES" -> "1";
            default -> null;
        };
    }

    private String readString(Map<String, Object> config, String key, String fallback) {
        if (config == null || !config.containsKey(key)) {
            return cleanText(fallback);
        }

        Object value = config.get(key);
        if (value == null) {
            return cleanText(fallback);
        }

        String text = value.toString().trim();
        return text.isEmpty() ? cleanText(fallback) : text;
    }

    private String cleanText(String value) {
        if (value == null) {
            return null;
        }

        String clean = value.trim();
        return clean.isEmpty() ? null : clean;
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    private String extractMetaMessageId(String body) {
        if (body == null || body.isBlank()) {
            return "META-WA-SENT";
        }

        int idIndex = body.indexOf("\"id\"");
        if (idIndex < 0) {
            return "META-WA-SENT";
        }

        int colon = body.indexOf(":", idIndex);
        if (colon < 0) {
            return "META-WA-SENT";
        }

        int firstQuote = body.indexOf("\"", colon + 1);
        if (firstQuote < 0) {
            return "META-WA-SENT";
        }

        int secondQuote = body.indexOf("\"", firstQuote + 1);
        if (secondQuote < 0) {
            return "META-WA-SENT";
        }

        return body.substring(firstQuote + 1, secondQuote);
    }

    private String limit(String value, int max) {
        if (value == null) {
            return "";
        }

        return value.length() <= max ? value : value.substring(0, max);
    }
}
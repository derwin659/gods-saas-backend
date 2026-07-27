package com.gods.saas.service.impl;

import com.gods.saas.domain.model.Notification;
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
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsappNotificationSenderMetaCloudImpl {

    private static final String PHONE_NUMBER_ID_KEY = "whatsappPhoneNumberId";
    private static final String ACCESS_TOKEN_KEY = "whatsappAccessToken";
    private static final String OWNER_BOOKING_TEMPLATE_NAME_KEY = "whatsappOwnerBookingTemplateName";
    private static final String OWNER_BOOKING_TEMPLATE_LANGUAGE_KEY = "whatsappOwnerBookingTemplateLanguage";
    private static final String DEFAULT_OWNER_BOOKING_TEMPLATE_NAME = "owner_new_booking_v1";
    private static final String DEFAULT_OWNER_BOOKING_TEMPLATE_LANGUAGE = "es";

    @Value("${whatsapp.meta.phone-number-id:}")
    private String defaultPhoneNumberId;

    @Value("${whatsapp.meta.access-token:}")
    private String defaultAccessToken;

    private final TenantSettingsRepository tenantSettingsRepository;
    private final WhatsappRecipientResolver recipientResolver;
    private final OwnerBookingWhatsappPayloadFactory ownerBookingPayloadFactory;

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

        WhatsappRecipientResolver.Recipient recipient = recipientResolver.resolve(notification);
        String phone = recipient.phoneDigits();

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

            String body = buildRequestBody(notification, phone, message, config);

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
                    "META WHATSAPP SENT => notificationId={}, customerId={}, userId={}, phone={}",
                    notification.getId(),
                    recipient.customerId(),
                    recipient.userId(),
                    phone
            );

            return extractMetaMessageId(response.body());

        } catch (Exception e) {
            throw new RuntimeException("No se pudo enviar WhatsApp Meta Cloud: " + e.getMessage(), e);
        }
    }

    private String buildRequestBody(
            Notification notification,
            String phone,
            String message,
            Map<String, Object> config
    ) {
        OwnerBookingWhatsappPayloadFactory.Payload payload = ownerBookingPayloadFactory
                .from(notification)
                .orElse(null);

        if (payload == null) {
            return """
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
        }

        String templateName = readString(
                config,
                OWNER_BOOKING_TEMPLATE_NAME_KEY,
                DEFAULT_OWNER_BOOKING_TEMPLATE_NAME
        );
        String language = readString(
                config,
                OWNER_BOOKING_TEMPLATE_LANGUAGE_KEY,
                DEFAULT_OWNER_BOOKING_TEMPLATE_LANGUAGE
        );
        String parameters = String.join(",",
                templateParameter(payload.appointmentId()),
                templateParameter(payload.customerName()),
                templateParameter(payload.customerPhone()),
                templateParameter(payload.businessAndBranch()),
                templateParameter(payload.serviceName()),
                templateParameter(payload.professionalName()),
                templateParameter(payload.date()),
                templateParameter(payload.schedule()),
                templateParameter(payload.paymentSummary()),
                templateParameter(payload.agendaUrl())
        );

        return """
                {
                  "messaging_product": "whatsapp",
                  "recipient_type": "individual",
                  "to": "%s",
                  "type": "template",
                  "template": {
                    "name": "%s",
                    "language": {"code": "%s"},
                    "components": [
                      {
                        "type": "body",
                        "parameters": [%s]
                      }
                    ]
                  }
                }
                """.formatted(
                escapeJson(phone),
                escapeJson(templateName),
                escapeJson(language),
                parameters
        );
    }

    private String templateParameter(String value) {
        return "{\"type\":\"text\",\"text\":\"" + escapeJson(value) + "\"}";
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
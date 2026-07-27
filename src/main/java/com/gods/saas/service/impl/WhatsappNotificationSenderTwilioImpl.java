package com.gods.saas.service.impl;

import com.gods.saas.domain.model.Notification;
import com.gods.saas.domain.model.TenantSettings;
import com.gods.saas.domain.repository.TenantSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsappNotificationSenderTwilioImpl {

    private static final String FROM_NUMBER_KEY = "twilioWhatsappFromNumber";
    private static final String MESSAGING_SERVICE_SID_KEY = "twilioMessagingServiceSid";
    private static final String OWNER_BOOKING_CONTENT_SID_KEY = "twilioOwnerBookingContentSid";

    @Value("${whatsapp.twilio.enabled:false}")
    private boolean enabled;

    @Value("${whatsapp.twilio.account-sid:}")
    private String defaultAccountSid;

    @Value("${whatsapp.twilio.auth-token:}")
    private String defaultAuthToken;

    @Value("${whatsapp.twilio.from-number:}")
    private String defaultFromNumber;

    @Value("${whatsapp.twilio.messaging-service-sid:}")
    private String defaultMessagingServiceSid;

    @Value("${whatsapp.twilio.status-callback-url:}")
    private String statusCallbackUrl;
    @Value("${whatsapp.twilio.owner-booking-content-sid:}")
    private String defaultOwnerBookingContentSid;

    private final TenantSettingsRepository tenantSettingsRepository;
    private final WhatsappRecipientResolver recipientResolver;
    private final OwnerBookingWhatsappPayloadFactory ownerBookingPayloadFactory;

    public String send(Notification notification) {
        if (!enabled) {
            throw new RuntimeException("Twilio WhatsApp no esta habilitado en el backend.");
        }

        if (notification == null) {
            throw new IllegalArgumentException("La notificacion no puede ser null");
        }

        if (notification.getTenant() == null || notification.getTenant().getId() == null) {
            throw new IllegalArgumentException("La notificacion no tiene tenant valido");
        }

        WhatsappRecipientResolver.Recipient recipient = recipientResolver.resolve(notification);
        String to = "+" + recipient.phoneDigits();

        String message = cleanText(notification.getMessage());
        if (message == null) {
            throw new RuntimeException("La notificacion no tiene mensaje");
        }

        Map<String, Object> config = tenantSettingsRepository
                .findByTenant_Id(notification.getTenant().getId())
                .map(TenantSettings::getScheduleConfig)
                .orElse(Map.of());

        String accountSid = cleanText(defaultAccountSid);
        String authToken = cleanText(defaultAuthToken);
        String fromNumber = normalizeConfiguredSender(
                readString(
                        config,
                        FROM_NUMBER_KEY,
                        readString(config, OwnerWhatsappSettingsService.SENDER_PHONE_KEY, defaultFromNumber)
                )
        );
        String messagingServiceSid = cleanText(readString(config, MESSAGING_SERVICE_SID_KEY, defaultMessagingServiceSid));
        String ownerBookingContentSid = cleanText(readString(
                config,
                OWNER_BOOKING_CONTENT_SID_KEY,
                defaultOwnerBookingContentSid
        ));
        OwnerBookingWhatsappPayloadFactory.Payload ownerBookingPayload = ownerBookingPayloadFactory
                .from(notification)
                .orElse(null);

        if (accountSid == null) {
            throw new RuntimeException("Falta configurar TWILIO_ACCOUNT_SID");
        }

        if (authToken == null) {
            throw new RuntimeException("Falta configurar TWILIO_AUTH_TOKEN");
        }

        if (messagingServiceSid == null && fromNumber == null) {
            throw new RuntimeException("Falta configurar TWILIO_WHATSAPP_FROM_NUMBER o TWILIO_MESSAGING_SERVICE_SID");
        }

        try {
            String url = "https://api.twilio.com/2010-04-01/Accounts/" + urlEncode(accountSid) + "/Messages.json";
            StringBuilder body = new StringBuilder();
            appendForm(body, "To", "whatsapp:" + to);
            if (ownerBookingPayload != null) {
                if (ownerBookingContentSid == null) {
                    throw new RuntimeException(
                            "Falta configurar TWILIO_OWNER_BOOKING_CONTENT_SID para avisos de reserva"
                    );
                }
                appendForm(body, "ContentSid", ownerBookingContentSid);
                appendForm(body, "ContentVariables", buildContentVariables(ownerBookingPayload));
            } else {
                appendForm(body, "Body", message);
            }

            if (messagingServiceSid != null) {
                appendForm(body, "MessagingServiceSid", messagingServiceSid);
            } else {
                appendForm(body, "From", "whatsapp:" + fromNumber);
            }

            String callback = cleanText(statusCallbackUrl);
            if (callback != null) {
                appendForm(body, "StatusCallback", callback);
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Basic " + basicAuth(accountSid, authToken))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error(
                        "TWILIO WHATSAPP FAILED => notificationId={}, status={}, body={}",
                        notification.getId(),
                        response.statusCode(),
                        response.body()
                );
                throw new RuntimeException("Twilio WhatsApp error " + response.statusCode() + ": " + limit(response.body(), 300));
            }

            log.info(
                    "TWILIO WHATSAPP SENT => notificationId={}, customerId={}, userId={}, phone={}",
                    notification.getId(),
                    recipient.customerId(),
                    recipient.userId(),
                    to
            );

            return extractJsonString(response.body(), "sid", "TWILIO-WA-SENT");
        } catch (Exception e) {
            throw new RuntimeException("No se pudo enviar WhatsApp Twilio: " + e.getMessage(), e);
        }
    }

    private String buildContentVariables(OwnerBookingWhatsappPayloadFactory.Payload payload) {
        return "{" +
                "\"1\":\"" + escapeJson(payload.appointmentId()) + "\"," +
                "\"2\":\"" + escapeJson(payload.customerName()) + "\"," +
                "\"3\":\"" + escapeJson(payload.customerPhone()) + "\"," +
                "\"4\":\"" + escapeJson(payload.businessAndBranch()) + "\"," +
                "\"5\":\"" + escapeJson(payload.serviceName()) + "\"," +
                "\"6\":\"" + escapeJson(payload.professionalName()) + "\"," +
                "\"7\":\"" + escapeJson(payload.date()) + "\"," +
                "\"8\":\"" + escapeJson(payload.schedule()) + "\"," +
                "\"9\":\"" + escapeJson(payload.paymentSummary()) + "\"," +
                "\"10\":\"" + escapeJson(payload.agendaUrl()) + "\"" +
                "}";
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
    private String normalizeConfiguredSender(String rawPhone) {
        String clean = cleanText(rawPhone);
        if (clean == null) {
            return null;
        }

        clean = clean.replace("whatsapp:", "").trim();
        String digits = clean.replaceAll("[^0-9+]", "");

        if (digits.isBlank()) {
            return null;
        }

        if (digits.startsWith("+")) {
            return digits;
        }

        if (digits.startsWith("00") && digits.length() > 2) {
            return "+" + digits.substring(2);
        }

        return "+" + digits.replaceAll("[^0-9]", "");
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

    private void appendForm(StringBuilder body, String key, String value) {
        if (body.length() > 0) {
            body.append("&");
        }
        body.append(urlEncode(key)).append("=").append(urlEncode(value));
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String basicAuth(String accountSid, String authToken) {
        String value = accountSid + ":" + authToken;
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String extractJsonString(String body, String key, String fallback) {
        if (body == null || body.isBlank() || key == null || key.isBlank()) {
            return fallback;
        }

        String token = "\"" + key + "\"";
        int keyIndex = body.indexOf(token);
        if (keyIndex < 0) {
            return fallback;
        }

        int colon = body.indexOf(":", keyIndex);
        if (colon < 0) {
            return fallback;
        }

        int firstQuote = body.indexOf("\"", colon + 1);
        if (firstQuote < 0) {
            return fallback;
        }

        int secondQuote = body.indexOf("\"", firstQuote + 1);
        if (secondQuote < 0) {
            return fallback;
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

package com.gods.saas.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gods.saas.domain.model.Notification;
import com.gods.saas.domain.model.TenantSettings;
import com.gods.saas.domain.repository.TenantSettingsRepository;
import com.gods.saas.utils.RegionalDefaults;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CentralWhatsappSenderService {

    private static final String DEFAULT_BOOKING_TEMPLATE = "owner_new_booking_v1";
    private static final String DEFAULT_LANGUAGE = "es";

    @Value("${whatsapp.central.enabled:false}")
    private boolean centralEnabled;

    @Value("${whatsapp.central.provider:TWILIO}")
    private String centralProvider;

    @Value("${whatsapp.central.sender-label:GODS Notificaciones}")
    private String centralSenderLabel;

    @Value("${whatsapp.meta.phone-number-id:}")
    private String metaPhoneNumberId;

    @Value("${whatsapp.meta.access-token:}")
    private String metaAccessToken;

    @Value("${whatsapp.central.meta.booking-template-name:" + DEFAULT_BOOKING_TEMPLATE + "}")
    private String metaBookingTemplateName;

    @Value("${whatsapp.central.meta.template-language:" + DEFAULT_LANGUAGE + "}")
    private String metaTemplateLanguage;

    @Value("${whatsapp.central.meta.booking-template-name-pt:}")
    private String metaBookingTemplateNamePt;

    @Value("${whatsapp.central.meta.booking-template-name-en:}")
    private String metaBookingTemplateNameEn;

    @Value("${whatsapp.twilio.enabled:false}")
    private boolean twilioEnabled;

    @Value("${whatsapp.twilio.account-sid:}")
    private String twilioAccountSid;

    @Value("${whatsapp.twilio.auth-token:}")
    private String twilioAuthToken;

    @Value("${whatsapp.twilio.from-number:}")
    private String twilioFromNumber;

    @Value("${whatsapp.twilio.messaging-service-sid:}")
    private String twilioMessagingServiceSid;

    @Value("${whatsapp.twilio.status-callback-url:}")
    private String twilioStatusCallbackUrl;

    @Value("${whatsapp.twilio.owner-booking-content-sid:}")
    private String twilioBookingContentSid;

    @Value("${whatsapp.twilio.owner-booking-content-sid-pt:}")
    private String twilioBookingContentSidPt;

    @Value("${whatsapp.twilio.owner-booking-content-sid-en:}")
    private String twilioBookingContentSidEn;

    @Value("${whatsapp.twilio.inbound-webhook-url:}")
    private String twilioInboundWebhookUrl;

    private final ObjectMapper objectMapper;
    private final TenantSettingsRepository tenantSettingsRepository;
    private final WhatsappRecipientResolver recipientResolver;
    private final OwnerBookingWhatsappPayloadFactory ownerBookingPayloadFactory;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public boolean isConfigured() {
        if (!centralEnabled) return false;

        return switch (provider()) {
            case "META_CLOUD" -> hasText(metaPhoneNumberId) && hasText(metaAccessToken);
            case "TWILIO" -> twilioEnabled
                    && hasText(twilioAccountSid)
                    && hasText(twilioAuthToken)
                    && (hasText(twilioMessagingServiceSid) || hasText(twilioFromNumber))
                    && hasText(twilioBookingContentSid);
            default -> false;
        };
    }

    public String provider() {
        String value = clean(centralProvider);
        return value == null ? "TWILIO" : value.toUpperCase(Locale.ROOT);
    }

    public String senderLabel() {
        String value = clean(centralSenderLabel);
        return value == null ? "GODS Notificaciones" : value;
    }

    public boolean isInboundVerificationConfigured() {
        return isConfigured()
                && "TWILIO".equals(provider())
                && hasText(twilioFromNumber)
                && hasText(twilioInboundWebhookUrl);
    }

    public String verificationMessage(Long userId, String code) {
        return "VERIFICAR GODS " + userId + " " + code;
    }

    public String verificationChatUrl(Long userId, String code) {
        if (!isInboundVerificationConfigured()) {
            throw new RuntimeException("Falta configurar el webhook entrante de WhatsApp en Twilio.");
        }
        String number = normalizeSender(twilioFromNumber).replaceAll("[^0-9]", "");
        return "https://wa.me/" + number + "?text=" + urlEncode(verificationMessage(userId, code));
    }

    public String sendBooking(Notification notification) {
        requireConfigured();

        WhatsappRecipientResolver.Recipient recipient = recipientResolver.resolve(notification);
        TemplateSelection template = resolveBookingTemplate(notification);
        OwnerBookingWhatsappPayloadFactory.Payload payload = ownerBookingPayloadFactory
                .from(notification)
                .orElseThrow(() -> new RuntimeException("No se pudo construir la plantilla de nueva reserva"));

        List<String> parameters = List.of(
                payload.appointmentId(),
                payload.customerName(),
                payload.customerPhone(),
                payload.businessAndBranch(),
                payload.serviceName(),
                payload.professionalName(),
                payload.date(),
                payload.schedule(),
                payload.paymentSummary(),
                payload.agendaUrl()
        );

        return sendTemplate(
                recipient.phoneDigits(),
                template.metaTemplateName(),
                template.metaLanguage(),
                template.twilioContentSid(),
                parameters,
                "BOOKING",
                notification.getId()
        );
    }

    private String sendTemplate(
            String phoneDigits,
            String metaTemplateName,
            String metaLanguage,
            String twilioContentSid,
            List<String> parameters,
            String event,
            Long notificationId
    ) {
        try {
            String externalId = switch (provider()) {
                case "META_CLOUD" -> sendMeta(phoneDigits, metaTemplateName, metaLanguage, parameters);
                case "TWILIO" -> sendTwilio(phoneDigits, twilioContentSid, parameters);
                default -> throw new RuntimeException("Proveedor central no soportado: " + provider());
            };

            log.info(
                    "CENTRAL WHATSAPP SENT => event={}, notificationId={}, provider={}, recipient={}",
                    event,
                    notificationId,
                    provider(),
                    mask(phoneDigits)
            );
            return externalId;
        } catch (Exception e) {
            throw new RuntimeException("No se pudo enviar desde GODS Notificaciones: " + e.getMessage(), e);
        }
    }

    private String sendMeta(
            String phoneDigits,
            String templateName,
            String templateLanguage,
            List<String> parameters
    ) throws Exception {
        if (!hasText(templateName)) {
            throw new RuntimeException("Falta el nombre de la plantilla Meta");
        }

        List<Map<String, Object>> templateParameters = new ArrayList<>();
        for (String parameter : parameters) {
            templateParameters.add(Map.of(
                    "type", "text",
                    "text", parameter == null ? "" : parameter
            ));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("recipient_type", "individual");
        body.put("to", phoneDigits);
        body.put("type", "template");
        body.put("template", Map.of(
                "name", templateName,
                "language", Map.of("code", clean(templateLanguage) == null
                        ? DEFAULT_LANGUAGE
                        : templateLanguage.trim()),
                "components", List.of(Map.of(
                        "type", "body",
                        "parameters", templateParameters
                ))
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://graph.facebook.com/v20.0/"
                        + metaPhoneNumberId.trim() + "/messages"))
                .header("Authorization", "Bearer " + metaAccessToken.trim())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        ensureSuccess("Meta WhatsApp", response);

        JsonNode json = objectMapper.readTree(response.body());
        JsonNode messages = json.path("messages");
        if (messages.isArray() && !messages.isEmpty() && messages.get(0).hasNonNull("id")) {
            return messages.get(0).get("id").asText();
        }
        return "META-WA-SENT";
    }

    private String sendTwilio(String phoneDigits, String contentSid, List<String> parameters) throws Exception {
        if (!hasText(contentSid)) {
            throw new RuntimeException("Falta configurar el Content SID de Twilio");
        }

        Map<String, String> variables = new LinkedHashMap<>();
        for (int i = 0; i < parameters.size(); i++) {
            variables.put(String.valueOf(i + 1), parameters.get(i) == null ? "" : parameters.get(i));
        }

        StringBuilder form = new StringBuilder();
        appendForm(form, "To", "whatsapp:+" + phoneDigits);
        appendForm(form, "ContentSid", contentSid.trim());
        appendForm(form, "ContentVariables", objectMapper.writeValueAsString(variables));

        String messagingServiceSid = clean(twilioMessagingServiceSid);
        if (messagingServiceSid != null) {
            appendForm(form, "MessagingServiceSid", messagingServiceSid);
        } else {
            String from = normalizeSender(twilioFromNumber);
            if (from == null) {
                throw new RuntimeException("Falta configurar TWILIO_WHATSAPP_FROM_NUMBER");
            }
            appendForm(form, "From", "whatsapp:" + from);
        }

        String callback = clean(twilioStatusCallbackUrl);
        if (callback != null) {
            appendForm(form, "StatusCallback", callback);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.twilio.com/2010-04-01/Accounts/"
                        + urlEncode(twilioAccountSid.trim()) + "/Messages.json"))
                .header("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                        (twilioAccountSid.trim() + ":" + twilioAuthToken.trim())
                                .getBytes(StandardCharsets.UTF_8)
                ))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        ensureSuccess("Twilio WhatsApp", response);

        JsonNode json = objectMapper.readTree(response.body());
        return json.hasNonNull("sid") ? json.get("sid").asText() : "TWILIO-WA-SENT";
    }

    private void ensureSuccess(String providerName, HttpResponse<String> response) {
        if (response.statusCode() >= 200 && response.statusCode() < 300) return;
        throw new RuntimeException(providerName + " error " + response.statusCode()
                + ": " + limit(response.body(), 300));
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new RuntimeException(
                    "GODS Notificaciones aun no esta configurado completamente en el backend"
            );
        }
    }

    private void appendForm(StringBuilder body, String key, String value) {
        if (body.length() > 0) body.append("&");
        body.append(urlEncode(key)).append("=").append(urlEncode(value));
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private TemplateSelection resolveBookingTemplate(Notification notification) {
        String locale = resolveRecipientLocale(notification);
        boolean twilioProvider = "TWILIO".equals(provider());
        boolean portugueseAvailable = twilioProvider
                ? hasText(twilioBookingContentSidPt)
                : hasText(metaBookingTemplateNamePt);
        boolean englishAvailable = twilioProvider
                ? hasText(twilioBookingContentSidEn)
                : hasText(metaBookingTemplateNameEn);
        if ("pt-BR".equals(locale) && portugueseAvailable) {
            return new TemplateSelection(
                    hasText(metaBookingTemplateNamePt) ? metaBookingTemplateNamePt.trim() : metaBookingTemplateName,
                    "pt_BR",
                    hasText(twilioBookingContentSidPt) ? twilioBookingContentSidPt.trim() : twilioBookingContentSid
            );
        }
        if ("en-US".equals(locale) && englishAvailable) {
            return new TemplateSelection(
                    hasText(metaBookingTemplateNameEn) ? metaBookingTemplateNameEn.trim() : metaBookingTemplateName,
                    "en_US",
                    hasText(twilioBookingContentSidEn) ? twilioBookingContentSidEn.trim() : twilioBookingContentSid
            );
        }
        return new TemplateSelection(
                metaBookingTemplateName,
                hasText(metaTemplateLanguage) ? metaTemplateLanguage.trim() : DEFAULT_LANGUAGE,
                twilioBookingContentSid
        );
    }

    private String resolveRecipientLocale(Notification notification) {
        String locale = null;
        if (notification != null && notification.getUser() != null) {
            locale = notification.getUser().getPreferredLocale();
        } else if (notification != null && notification.getCustomer() != null) {
            locale = notification.getCustomer().getPreferredLocale();
        }

        String country = notification != null && notification.getTenant() != null
                ? notification.getTenant().getPais()
                : null;
        if (!hasText(locale) && notification != null && notification.getTenant() != null
                && notification.getTenant().getId() != null) {
            locale = tenantSettingsRepository.findByTenant_Id(notification.getTenant().getId())
                    .map(TenantSettings::getLanguage)
                    .orElse(null);
        }
        return RegionalDefaults.normalizeLocale(locale, country);
    }

    private record TemplateSelection(
            String metaTemplateName,
            String metaLanguage,
            String twilioContentSid
    ) {
    }


    private String normalizeSender(String value) {
        String clean = clean(value);
        if (clean == null) return null;
        clean = clean.replace("whatsapp:", "").replaceAll("[^0-9+]", "");
        if (clean.startsWith("+")) return clean;
        if (clean.startsWith("00") && clean.length() > 2) return "+" + clean.substring(2);
        return "+" + clean.replaceAll("[^0-9]", "");
    }

    private String mask(String value) {
        if (value == null || value.length() <= 4) return "****";
        return "****" + value.substring(value.length() - 4);
    }

    private String clean(String value) {
        if (value == null) return null;
        String result = value.trim();
        return result.isEmpty() ? null : result;
    }

    private boolean hasText(String value) {
        return clean(value) != null;
    }

    private String limit(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }
}
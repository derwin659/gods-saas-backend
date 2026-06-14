package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.UpdateWhatsappSettingsRequest;
import com.gods.saas.domain.dto.response.WhatsappSettingsResponse;
import com.gods.saas.domain.model.TenantSettings;
import com.gods.saas.domain.repository.TenantSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OwnerWhatsappSettingsService {

    public static final String POST_SALE_MESSAGE_ENABLED_KEY = "whatsappPostSaleMessageEnabled";
    public static final String INCLUDE_APP_DOWNLOAD_LINK_KEY = "whatsappIncludeAppDownloadLink";
    public static final String INCLUDE_BOOKING_LINK_KEY = "whatsappIncludeBookingLink";
    public static final String REMINDER_60_ENABLED_KEY = "whatsappReminder60Enabled";
    public static final String REMINDER_24H_ENABLED_KEY = "whatsappReminder24hEnabled";
    public static final String INACTIVE_CUSTOMER_FOLLOW_UP_ENABLED_KEY = "whatsappInactiveCustomerFollowUpEnabled";
    public static final String APP_DOWNLOAD_URL_KEY = "whatsappAppDownloadUrl";
    public static final String PROVIDER_KEY = "whatsappProvider";
    public static final String CONNECTION_STATUS_KEY = "whatsappConnectionStatus";
    public static final String SENDER_PHONE_KEY = "whatsappSenderPhone";
    public static final String SENDER_LABEL_KEY = "whatsappSenderLabel";

    private static final String DEFAULT_APP_DOWNLOAD_URL =
            "https://play.google.com/store/apps/details?id=com.gods.barberia";
    private static final String DEFAULT_PROVIDER = "MANUAL";
    private static final String DEFAULT_CONNECTION_STATUS = "NOT_CONNECTED";

    private final TenantSettingsRepository tenantSettingsRepository;

    @Value("${whatsapp.twilio.enabled:false}")
    private boolean twilioEnabled;

    @Value("${whatsapp.twilio.from-number:}")
    private String twilioFromNumber;

    @Value("${whatsapp.twilio.messaging-service-sid:}")
    private String twilioMessagingServiceSid;

    @Transactional(readOnly = true)
    public WhatsappSettingsResponse getSettings(Long tenantId) {
        TenantSettings settings = resolveSettings(tenantId);
        Map<String, Object> config = settings.getScheduleConfig();
        String provider = readString(config, PROVIDER_KEY, DEFAULT_PROVIDER);
        String connectionStatus = readString(config, CONNECTION_STATUS_KEY, DEFAULT_CONNECTION_STATUS);
        String senderPhone = readString(config, SENDER_PHONE_KEY, "");

        if ("TWILIO".equalsIgnoreCase(provider) && senderPhone.isBlank()) {
            senderPhone = safeText(twilioFromNumber);
        }

        return WhatsappSettingsResponse.builder()
                .postSaleMessageEnabled(readBoolean(config, POST_SALE_MESSAGE_ENABLED_KEY, true))
                .includeAppDownloadLink(readBoolean(config, INCLUDE_APP_DOWNLOAD_LINK_KEY, true))
                .includeBookingLink(readBoolean(config, INCLUDE_BOOKING_LINK_KEY, true))
                .appointmentReminder60Enabled(readBoolean(config, REMINDER_60_ENABLED_KEY, true))
                .appointmentReminder24hEnabled(readBoolean(config, REMINDER_24H_ENABLED_KEY, false))
                .inactiveCustomerFollowUpEnabled(readBoolean(config, INACTIVE_CUSTOMER_FOLLOW_UP_ENABLED_KEY, false))
                .appDownloadUrl(readString(config, APP_DOWNLOAD_URL_KEY, DEFAULT_APP_DOWNLOAD_URL))
                .provider(provider)
                .connectionStatus(connectionStatus)
                .senderPhone(senderPhone)
                .senderLabel(readString(config, SENDER_LABEL_KEY, ""))
                .connected(isConnected(provider, connectionStatus, senderPhone))
                .build();
    }

    @Transactional
    public WhatsappSettingsResponse updateSettings(Long tenantId, UpdateWhatsappSettingsRequest request) {
        if (request == null) {
            throw new RuntimeException("Ingresa la configuracion de WhatsApp.");
        }

        TenantSettings settings = resolveSettings(tenantId);
        Map<String, Object> config = settings.getScheduleConfig() == null
                ? new HashMap<>()
                : new HashMap<>(settings.getScheduleConfig());

        putBoolean(config, POST_SALE_MESSAGE_ENABLED_KEY, request.getPostSaleMessageEnabled());
        putBoolean(config, INCLUDE_APP_DOWNLOAD_LINK_KEY, request.getIncludeAppDownloadLink());
        putBoolean(config, INCLUDE_BOOKING_LINK_KEY, request.getIncludeBookingLink());
        putBoolean(config, REMINDER_60_ENABLED_KEY, request.getAppointmentReminder60Enabled());
        putBoolean(config, REMINDER_24H_ENABLED_KEY, request.getAppointmentReminder24hEnabled());
        putBoolean(config, INACTIVE_CUSTOMER_FOLLOW_UP_ENABLED_KEY, request.getInactiveCustomerFollowUpEnabled());

        if (request.getAppDownloadUrl() != null) {
            String url = request.getAppDownloadUrl().trim();
            if (url.isEmpty()) {
                config.remove(APP_DOWNLOAD_URL_KEY);
            } else if (!url.startsWith("http://") && !url.startsWith("https://")) {
                throw new RuntimeException("El link de descarga debe iniciar con http:// o https://");
            } else {
                config.put(APP_DOWNLOAD_URL_KEY, url);
            }
        }

        putText(config, PROVIDER_KEY, normalizeProvider(request.getProvider()));
        putText(config, CONNECTION_STATUS_KEY, normalizeConnectionStatus(request.getConnectionStatus()));
        putText(config, SENDER_PHONE_KEY, cleanPhone(request.getSenderPhone()));
        putText(config, SENDER_LABEL_KEY, cleanText(request.getSenderLabel()));

        settings.setScheduleConfig(config);
        settings.setUpdatedAt(LocalDateTime.now());
        tenantSettingsRepository.save(settings);

        return getSettings(tenantId);
    }

    private TenantSettings resolveSettings(Long tenantId) {
        return tenantSettingsRepository.findByTenant_Id(tenantId)
                .orElseThrow(() -> new RuntimeException("No existe configuracion del negocio."));
    }

    private void putBoolean(Map<String, Object> config, String key, Boolean value) {
        if (value != null) {
            config.put(key, value);
        }
    }

    private void putText(Map<String, Object> config, String key, String value) {
        if (value == null) {
            return;
        }

        if (value.isBlank()) {
            config.remove(key);
        } else {
            config.put(key, value);
        }
    }

    private String normalizeProvider(String value) {
        String text = cleanText(value);
        if (text == null || text.isBlank()) return null;

        text = text.toUpperCase();
        return switch (text) {
            case "MANUAL", "MOCK", "META_CLOUD", "TWILIO", "BAILEYS" -> text;
            default -> throw new RuntimeException("Proveedor de WhatsApp no valido.");
        };
    }

    private String normalizeConnectionStatus(String value) {
        String text = cleanText(value);
        if (text == null || text.isBlank()) return null;

        text = text.toUpperCase();
        return switch (text) {
            case "NOT_CONNECTED", "PENDING", "CONNECTED", "PAUSED", "ERROR" -> text;
            default -> throw new RuntimeException("Estado de WhatsApp no valido.");
        };
    }

    private String cleanPhone(String value) {
        String text = cleanText(value);
        if (text == null) return null;

        String cleaned = text.replaceAll("[^0-9+]", "");
        if (cleaned.length() > 25) {
            throw new RuntimeException("El numero de WhatsApp es demasiado largo.");
        }
        return cleaned;
    }

    private String cleanText(String value) {
        if (value == null) return null;
        String text = value.trim();
        return text.isEmpty() ? "" : text;
    }

    private boolean readBoolean(Map<String, Object> config, String key, boolean fallback) {
        if (config == null || !config.containsKey(key)) {
            return fallback;
        }

        Object value = config.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }

        if (value instanceof String text) {
            return Boolean.parseBoolean(text.trim());
        }

        return fallback;
    }

    private String readString(Map<String, Object> config, String key, String fallback) {
        if (config == null || !config.containsKey(key)) {
            return fallback;
        }

        Object value = config.get(key);
        if (value == null) {
            return fallback;
        }

        String text = value.toString().trim();
        return text.isEmpty() ? fallback : text;
    }

    private boolean isConnected(String provider, String connectionStatus, String senderPhone) {
        if (!"CONNECTED".equalsIgnoreCase(connectionStatus)) {
            return false;
        }

        if (!"TWILIO".equalsIgnoreCase(provider)) {
            return true;
        }

        return twilioEnabled
                && (!safeText(senderPhone).isBlank()
                || !safeText(twilioFromNumber).isBlank()
                || !safeText(twilioMessagingServiceSid).isBlank());
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }
}

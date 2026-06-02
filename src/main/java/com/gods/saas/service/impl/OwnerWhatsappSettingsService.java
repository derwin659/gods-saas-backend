package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.UpdateWhatsappSettingsRequest;
import com.gods.saas.domain.dto.response.WhatsappSettingsResponse;
import com.gods.saas.domain.model.TenantSettings;
import com.gods.saas.domain.repository.TenantSettingsRepository;
import lombok.RequiredArgsConstructor;
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

    private static final String DEFAULT_APP_DOWNLOAD_URL =
            "https://play.google.com/store/apps/details?id=com.gods.barberia";

    private final TenantSettingsRepository tenantSettingsRepository;

    @Transactional(readOnly = true)
    public WhatsappSettingsResponse getSettings(Long tenantId) {
        TenantSettings settings = resolveSettings(tenantId);
        Map<String, Object> config = settings.getScheduleConfig();

        return WhatsappSettingsResponse.builder()
                .postSaleMessageEnabled(readBoolean(config, POST_SALE_MESSAGE_ENABLED_KEY, true))
                .includeAppDownloadLink(readBoolean(config, INCLUDE_APP_DOWNLOAD_LINK_KEY, true))
                .includeBookingLink(readBoolean(config, INCLUDE_BOOKING_LINK_KEY, true))
                .appointmentReminder60Enabled(readBoolean(config, REMINDER_60_ENABLED_KEY, true))
                .appointmentReminder24hEnabled(readBoolean(config, REMINDER_24H_ENABLED_KEY, false))
                .inactiveCustomerFollowUpEnabled(readBoolean(config, INACTIVE_CUSTOMER_FOLLOW_UP_ENABLED_KEY, false))
                .appDownloadUrl(readString(config, APP_DOWNLOAD_URL_KEY, DEFAULT_APP_DOWNLOAD_URL))
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
}

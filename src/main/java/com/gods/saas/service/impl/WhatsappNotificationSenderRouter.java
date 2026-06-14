package com.gods.saas.service.impl;

import com.gods.saas.domain.model.Notification;
import com.gods.saas.domain.model.TenantSettings;
import com.gods.saas.domain.repository.TenantSettingsRepository;
import com.gods.saas.service.impl.impl.WhatsappNotificationSender;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;

@Service
@Primary
@RequiredArgsConstructor
public class WhatsappNotificationSenderRouter implements WhatsappNotificationSender {

    private final TenantSettingsRepository tenantSettingsRepository;
    private final WhatsappNotificationSenderMockImpl mockSender;
    private final WhatsappNotificationSenderMetaCloudImpl metaCloudSender;
    private final WhatsappNotificationSenderTwilioImpl twilioSender;

    @Override
    public String send(Notification notification) {
        if (notification == null || notification.getTenant() == null || notification.getTenant().getId() == null) {
            throw new IllegalArgumentException("La notificación no tiene tenant válido");
        }

        Map<String, Object> config = tenantSettingsRepository
                .findByTenant_Id(notification.getTenant().getId())
                .map(TenantSettings::getScheduleConfig)
                .orElse(Map.of());

        String provider = readString(config, OwnerWhatsappSettingsService.PROVIDER_KEY, "MANUAL")
                .trim()
                .toUpperCase(Locale.ROOT);

        String connectionStatus = readString(config, OwnerWhatsappSettingsService.CONNECTION_STATUS_KEY, "NOT_CONNECTED")
                .trim()
                .toUpperCase(Locale.ROOT);

        if ("MANUAL".equals(provider)) {
            throw new RuntimeException("WhatsApp está en modo MANUAL. No se envía automáticamente.");
        }

        if ("MOCK".equals(provider)) {
            return mockSender.send(notification);
        }

        if ("META_CLOUD".equals(provider)) {
            if (!"CONNECTED".equals(connectionStatus)) {
                throw new RuntimeException("WhatsApp Meta Cloud no está conectado.");
            }

            return metaCloudSender.send(notification);
        }

        if ("TWILIO".equals(provider)) {
            if (!"CONNECTED".equals(connectionStatus)) {
                throw new RuntimeException("WhatsApp Twilio no esta conectado.");
            }

            return twilioSender.send(notification);
        }

        throw new RuntimeException("Proveedor de WhatsApp no soportado: " + provider);
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

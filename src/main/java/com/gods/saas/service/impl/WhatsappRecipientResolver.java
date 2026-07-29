package com.gods.saas.service.impl;

import com.gods.saas.domain.model.Notification;
import com.gods.saas.domain.model.Tenant;
import org.springframework.stereotype.Component;

@Component
public class WhatsappRecipientResolver {

    private final InternationalPhoneService internationalPhoneService;

    public WhatsappRecipientResolver(InternationalPhoneService internationalPhoneService) {
        this.internationalPhoneService = internationalPhoneService;
    }

    public Recipient resolve(Notification notification) {
        if (notification == null) {
            throw new IllegalArgumentException("La notificacion no puede ser null");
        }

        String rawPhone = null;
        Long customerId = null;
        Long userId = null;

        if (notification.getCustomer() != null) {
            rawPhone = notification.getCustomer().getTelefono();
            customerId = notification.getCustomer().getId();
        } else if (notification.getUser() != null) {
            rawPhone = notification.getUser().getPhone();
            userId = notification.getUser().getId();
        }

        String digits = normalizeDigits(rawPhone, notification.getTenant());
        if (digits == null || digits.isBlank()) {
            throw new RuntimeException("El destinatario no tiene telefono valido para WhatsApp");
        }

        return new Recipient(digits, customerId, userId);
    }

    public String normalizeDigits(String rawPhone, Tenant tenant) {
        return internationalPhoneService.normalizeDigitsOrNull(tenant, rawPhone);
    }

    public record Recipient(String phoneDigits, Long customerId, Long userId) {
    }
}
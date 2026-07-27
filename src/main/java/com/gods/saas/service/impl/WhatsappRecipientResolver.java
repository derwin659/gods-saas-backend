package com.gods.saas.service.impl;

import com.gods.saas.domain.model.Notification;
import com.gods.saas.domain.model.Tenant;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class WhatsappRecipientResolver {

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

    private String normalizeDigits(String rawPhone, Tenant tenant) {
        String digits = rawPhone == null ? "" : rawPhone.replaceAll("[^0-9]", "");
        if (digits.isBlank()) return null;

        if (digits.startsWith("00") && digits.length() > 2) {
            digits = digits.substring(2);
        }

        if (digits.length() >= 11) {
            return digits;
        }

        String country = tenant == null || tenant.getPais() == null
                ? ""
                : tenant.getPais().trim().toUpperCase(Locale.ROOT);
        String prefix = switch (country) {
            case "PE", "PERU", "PERÚ" -> "51";
            case "CO", "COLOMBIA" -> "57";
            case "MX", "MEXICO", "MÉXICO" -> "52";
            case "CL", "CHILE" -> "56";
            case "AR", "ARGENTINA" -> "54";
            case "BO", "BOLIVIA" -> "591";
            case "BR", "BRASIL", "BRAZIL" -> "55";
            case "UY", "URUGUAY" -> "598";
            case "PY", "PARAGUAY" -> "595";
            case "CR", "COSTA RICA" -> "506";
            case "DO", "REPUBLICA DOMINICANA", "REPÚBLICA DOMINICANA", "DOMINICAN REPUBLIC" -> "1";
            case "GT", "GUATEMALA" -> "502";
            case "US", "USA", "UNITED STATES" -> "1";
            default -> "";
        };

        if (!prefix.isBlank() && !digits.startsWith(prefix)) {
            digits = prefix + digits;
        }

        return digits;
    }

    public record Recipient(String phoneDigits, Long customerId, Long userId) {
    }
}
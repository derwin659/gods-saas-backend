package com.gods.saas.service.impl;

import com.gods.saas.domain.enums.NotificationType;
import com.gods.saas.domain.model.Notification;
import com.gods.saas.domain.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class TransactionalWhatsappPolicy {
    private final AppointmentRepository appointments;
    private final SaleRepository sales;
    private final TenantSettingsRepository settings;
    private final TenantTimeService time;
    private final BookingWaitlistRepository waitlist;
    @org.springframework.beans.factory.annotation.Value("${booking.waitlist.enabled:false}")
    private boolean waitlistEnabled;

    public static boolean isReminder(Notification n) {
        return n != null && Set.of(NotificationType.BOOKING_REMINDER_60, NotificationType.BOOKING_REMINDER_30,
            NotificationType.BOOKING_REMINDER_24H).contains(n.getType());
    }
    public boolean current(Notification n, boolean whatsapp) {
        if (n == null || n.getTenant() == null) return false;
        Long tenant = n.getTenant().getId();
        if (n.getType() == NotificationType.WAITLIST_AVAILABLE) {
            if (whatsapp || !waitlistEnabled || n.getReferenceId() == null || n.getCustomer() == null) return false;
            return waitlist.findById(n.getReferenceId()).filter(e ->
                Objects.equals(e.getTenantId(), tenant) && Objects.equals(e.getCustomerId(), n.getCustomer().getId())
                && "AVAILABLE".equals(e.getStatus()) && e.getOfferExpiresAt() != null && e.getOfferExpiresAt().isAfter(Instant.now())).isPresent();
        }
        var config = settings.findByTenant_Id(tenant).map(s -> s.getScheduleConfig()).orElse(Map.of());
        if (n.getType() == NotificationType.SALE_RECEIPT && n.getCustomer() != null) {
            if (!enabled(config, OwnerWhatsappSettingsService.POST_SALE_MESSAGE_ENABLED_KEY, true)) return false;
            return sales.findByIdAndTenant_Id(n.getReferenceId(), tenant).filter(s ->
                "APPROVED".equalsIgnoreCase(s.getPaymentValidationStatus()) && s.getCustomer() != null
                && Objects.equals(s.getCustomer().getId(), n.getCustomer().getId())).isPresent();
        }
        if (!isReminder(n)) return true;
        if (whatsapp && (n.getType() == NotificationType.BOOKING_REMINDER_30 || !enabled(config,
                n.getType() == NotificationType.BOOKING_REMINDER_24H ? OwnerWhatsappSettingsService.REMINDER_24H_ENABLED_KEY
                    : OwnerWhatsappSettingsService.REMINDER_60_ENABLED_KEY, n.getType() != NotificationType.BOOKING_REMINDER_24H))) return false;
        return appointments.findByIdAndTenant_Id(n.getReferenceId(), tenant).filter(a -> {
            if (!BookingReminderPolicy.isCandidate(a) || !Objects.equals(a.getCustomer().getId(), n.getCustomer() == null ? null : n.getCustomer().getId())) return false;
            LocalDateTime start = LocalDateTime.of(a.getFecha(), a.getHoraInicio());
            LocalDateTime now = time.now(tenant);
            if (!start.equals(n.getAppointmentStart()) || !start.isAfter(now)) return false;
            if (n.getType() == NotificationType.BOOKING_REMINDER_24H) return a.getFecha().equals(now.toLocalDate().plusDays(1));
            Duration remaining = Duration.between(now, start);
            int minutes = n.getType() == NotificationType.BOOKING_REMINDER_60 ? 60 : 30;
            return remaining.compareTo(Duration.ofMinutes(minutes)) <= 0 && remaining.compareTo(Duration.ofMinutes(minutes - 5)) >= 0;
        }).isPresent();
    }
    private boolean enabled(Map<String,Object> config, String key, boolean fallback) {
        if (config == null || !config.containsKey(key)) return fallback;
        return Boolean.TRUE.equals(config.get(key)) || "true".equalsIgnoreCase(String.valueOf(config.get(key)));
    }
}

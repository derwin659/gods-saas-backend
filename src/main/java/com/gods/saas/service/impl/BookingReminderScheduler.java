package com.gods.saas.service.impl;

import com.gods.saas.domain.enums.NotificationType;
import com.gods.saas.domain.model.Appointment;
import com.gods.saas.domain.repository.AppointmentRepository;
import com.gods.saas.domain.repository.TenantSettingsRepository;
import com.gods.saas.service.impl.impl.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;

@Component
@RequiredArgsConstructor
public class BookingReminderScheduler {
    private final AppointmentRepository appointmentRepository;
    private final NotificationService notificationService;
    private final TenantSettingsRepository tenantSettingsRepository;
    private final TenantTimeService tenantTimeService;

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void processReminders() {
        // A local date can differ from UTC; include tomorrow even across local midnight.
        LocalDate utcDate = LocalDate.now(ZoneOffset.UTC);
        for (Appointment a : appointmentRepository.findByFechaBetween(utcDate.minusDays(1), utcDate.plusDays(2))) {
            if (!BookingReminderPolicy.isCandidate(a)) continue;
            LocalDateTime now = tenantTimeService.now(a.getTenant().getId());
            Duration remaining = Duration.between(now, LocalDateTime.of(a.getFecha(), a.getHoraInicio()));
            if (remaining.compareTo(Duration.ofMinutes(60)) <= 0 && remaining.compareTo(Duration.ofMinutes(55)) >= 0)
                notificationService.notifyBookingReminder(a, NotificationType.BOOKING_REMINDER_60);
            if (remaining.compareTo(Duration.ofMinutes(30)) <= 0 && remaining.compareTo(Duration.ofMinutes(25)) >= 0)
                notificationService.notifyBookingReminder(a, NotificationType.BOOKING_REMINDER_30);
            if (a.getFecha().equals(now.toLocalDate().plusDays(1)) && reminder24Enabled(a))
                notificationService.notifyBookingReminder(a, NotificationType.BOOKING_REMINDER_24H);
        }
    }

    private boolean reminder24Enabled(Appointment a) {
        return tenantSettingsRepository.findByTenant_Id(a.getTenant().getId()).map(s -> {
            Object value = s.getScheduleConfig() == null ? null : s.getScheduleConfig().get(OwnerWhatsappSettingsService.REMINDER_24H_ENABLED_KEY);
            return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
        }).orElse(false);
    }
}

package com.gods.saas.service.impl;

import com.gods.saas.domain.enums.NotificationType;
import com.gods.saas.domain.model.Appointment;
import com.gods.saas.domain.repository.AppointmentRepository;
import com.gods.saas.domain.repository.TenantSettingsRepository;
import com.gods.saas.service.impl.impl.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class BookingReminderScheduler {

    private static final ZoneId ZONE = ZoneId.of("America/Lima");

    private final AppointmentRepository appointmentRepository;
    private final NotificationService notificationService;
    private final TenantSettingsRepository tenantSettingsRepository;

    @Scheduled(fixedDelay = 300000)
    public void processReminders() {
        LocalDate today = LocalDate.now(ZONE);
        LocalDate tomorrow = today.plusDays(1);
        LocalDateTime now = LocalDateTime.now(ZONE);

        process24HourReminders(tomorrow);

        List<Appointment> appointments = appointmentRepository.findByFecha(today);

        for (Appointment appointment : appointments) {
            if (!isReminderCandidate(appointment)) {
                continue;
            }

            LocalDateTime start = LocalDateTime.of(
                    appointment.getFecha(),
                    appointment.getHoraInicio()
            );

            long minutes = Duration.between(now, start).toMinutes();

            if (minutes >= 55 && minutes <= 60) {
                notificationService.notifyBookingReminder(
                        appointment,
                        NotificationType.BOOKING_REMINDER_60
                );
            }

            if (minutes >= 25 && minutes <= 30) {
                notificationService.notifyBookingReminder(
                        appointment,
                        NotificationType.BOOKING_REMINDER_30
                );
            }
        }
    }

    private void process24HourReminders(LocalDate reminderDate) {
        List<Appointment> appointments = appointmentRepository.findByFecha(reminderDate);

        for (Appointment appointment : appointments) {
            if (!isReminderCandidate(appointment)) {
                continue;
            }

            if (!isWhatsapp24HourReminderEnabled(appointment)) {
                continue;
            }

            notificationService.notifyBookingReminder(
                    appointment,
                    NotificationType.BOOKING_REMINDER_24H
            );
        }
    }

    private boolean isReminderCandidate(Appointment appointment) {
        if (appointment == null || appointment.getFecha() == null || appointment.getHoraInicio() == null) {
            return false;
        }

        String estado = appointment.getEstado() == null
                ? ""
                : appointment.getEstado().trim().toUpperCase();

        return !"CANCELADO".equals(estado)
                && !"CANCELADA".equals(estado)
                && !"CANCELLED".equals(estado)
                && !"COMPLETADO".equals(estado)
                && !"COMPLETADA".equals(estado)
                && !"COMPLETED".equals(estado)
                && !"FINALIZADO".equals(estado)
                && !"FINALIZADA".equals(estado);
    }

    private boolean isWhatsapp24HourReminderEnabled(Appointment appointment) {
        if (appointment.getTenant() == null || appointment.getTenant().getId() == null) {
            return false;
        }

        return tenantSettingsRepository.findByTenant_Id(appointment.getTenant().getId())
                .map(settings -> readBooleanConfig(
                        settings.getScheduleConfig(),
                        OwnerWhatsappSettingsService.REMINDER_24H_ENABLED_KEY,
                        false
                ))
                .orElse(false);
    }

    private boolean readBooleanConfig(Map<String, Object> config, String key, boolean fallback) {
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
}

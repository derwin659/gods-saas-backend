package com.gods.saas.service.impl;

import com.gods.saas.domain.enums.NotificationType;
import com.gods.saas.domain.model.Appointment;
import com.gods.saas.domain.repository.AppointmentRepository;
import com.gods.saas.service.impl.impl.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Component
@RequiredArgsConstructor
public class BookingReminderScheduler {

    private static final ZoneId ZONE = ZoneId.of("America/Lima");

    private final AppointmentRepository appointmentRepository;
    private final NotificationService notificationService;

    @Scheduled(fixedDelay = 300000)
    public void processReminders() {
        LocalDate today = LocalDate.now(ZONE);
        LocalDateTime now = LocalDateTime.now(ZONE);

        List<Appointment> appointments = appointmentRepository.findByFecha(today);

        for (Appointment appointment : appointments) {
            if (appointment.getFecha() == null || appointment.getHoraInicio() == null) {
                continue;
            }

            String estado = appointment.getEstado() == null
                    ? ""
                    : appointment.getEstado().trim().toUpperCase();

            if ("CANCELADO".equals(estado)
                    || "COMPLETADO".equals(estado)
                    || "FINALIZADO".equals(estado)) {
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
}
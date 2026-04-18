package com.gods.saas.service.impl;

import com.gods.saas.domain.enums.NotificationType;
import com.gods.saas.domain.model.Appointment;
import com.gods.saas.domain.repository.AppointmentRepository;
import com.gods.saas.service.impl.impl.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.List;

@Component
@RequiredArgsConstructor
public class BookingReminderScheduler {

    private final AppointmentRepository appointmentRepository;
    private final NotificationService notificationService;

    @Scheduled(fixedDelay = 300000)
    public void processReminders() {
        LocalDate today = LocalDate.now(ZoneId.of("America/Lima"));
        List<Appointment> appointments = appointmentRepository.findByFecha(today);

        LocalDateTime now = LocalDateTime.now(ZoneId.of("America/Lima"));

        for (Appointment a : appointments) {
            if (a.getFecha() == null || a.getHoraInicio() == null) continue;
            if (a.getEstado() == null) continue;

            String estado = a.getEstado().trim().toUpperCase();
            if ("CANCELADO".equals(estado) || "COMPLETADO".equals(estado) || "FINALIZADO".equals(estado)) {
                continue;
            }

            LocalDateTime start = LocalDateTime.of(a.getFecha(), a.getHoraInicio());
            long minutes = Duration.between(now, start).toMinutes();

            if (minutes >= 55 && minutes <= 60) {
                notificationService.notifyBookingReminder(a, NotificationType.BOOKING_REMINDER_60);
            }

            if (minutes >= 25 && minutes <= 30) {
                notificationService.notifyBookingReminder(a, NotificationType.BOOKING_REMINDER_30);
            }
        }
    }
}
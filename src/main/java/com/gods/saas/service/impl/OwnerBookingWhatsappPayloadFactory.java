package com.gods.saas.service.impl;

import com.gods.saas.domain.enums.NotificationType;
import com.gods.saas.domain.model.Appointment;
import com.gods.saas.domain.model.Notification;
import com.gods.saas.domain.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OwnerBookingWhatsappPayloadFactory {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final String AGENDA_URL = "https://www.supergodsapp.com/owner/agenda";

    private final AppointmentRepository appointmentRepository;

    public Optional<Payload> from(Notification notification) {
        if (notification == null
                || notification.getUser() == null
                || notification.getType() != NotificationType.BOOKING_CREATED
                || !"APPOINTMENT".equalsIgnoreCase(safe(notification.getReferenceType(), ""))
                || notification.getReferenceId() == null) {
            return Optional.empty();
        }

        Appointment appointment = appointmentRepository.findById(notification.getReferenceId()).orElse(null);
        if (appointment == null) {
            return Optional.empty();
        }

        String customerName = appointment.getCustomer() == null
                ? "Cliente"
                : fullName(
                        appointment.getCustomer().getNombres(),
                        appointment.getCustomer().getApellidos(),
                        "Cliente"
                );
        String customerPhone = appointment.getCustomer() == null
                ? "No registrado"
                : safe(appointment.getCustomer().getTelefono(), "No registrado");
        String tenantName = appointment.getTenant() == null
                ? "Negocio"
                : safe(appointment.getTenant().getNombre(), "Negocio");
        String branchName = appointment.getBranch() == null
                ? "Sede"
                : safe(appointment.getBranch().getNombre(), "Sede");
        String serviceName = appointment.getService() == null
                ? "Servicio"
                : safe(appointment.getService().getNombre(), "Servicio");
        String professionalName = appointment.getUser() == null
                ? "Sin profesional"
                : fullName(
                        appointment.getUser().getNombre(),
                        appointment.getUser().getApellido(),
                        "Sin profesional"
                );
        String date = appointment.getFecha() == null ? "Sin fecha" : appointment.getFecha().format(DATE_FMT);
        String start = appointment.getHoraInicio() == null ? "Sin hora" : appointment.getHoraInicio().format(TIME_FMT);
        String end = appointment.getHoraFin() == null ? "" : " - " + appointment.getHoraFin().format(TIME_FMT);
        String paymentSummary = paymentSummary(appointment);
        String agendaUrl = AGENDA_URL
                + "?appointmentId=" + appointment.getId()
                + "&branchId=" + (appointment.getBranch() == null ? "" : appointment.getBranch().getId())
                + "&date=" + (appointment.getFecha() == null ? "" : appointment.getFecha());
        return Optional.of(new Payload(

                String.valueOf(appointment.getId()),
                customerName,
                customerPhone,
                tenantName + " / " + branchName,
                serviceName,
                professionalName,
                date,
                start + end,
                paymentSummary,
                agendaUrl
        ));
    }

    private String paymentSummary(Appointment appointment) {
        String status = appointmentStatusLabel(appointment.getEstado());
        String total = appointment.getTotalAmount() == null
                ? "No informado"
                : money(appointment.getTotalAmount());

        if (!Boolean.TRUE.equals(appointment.getDepositRequired())) {
            return status + " | Sin adelanto | Total " + total;
        }

        String depositAmount = appointment.getDepositAmount() == null
                ? money(BigDecimal.ZERO)
                : money(appointment.getDepositAmount());
        String depositStatus = safe(appointment.getDepositStatus(), "PENDING_VALIDATION")
                .toUpperCase(Locale.ROOT);
        String depositLabel = switch (depositStatus) {
            case "PAID", "VALIDATED", "VALIDADO" -> "Adelanto validado " + depositAmount;
            case "REJECTED", "RECHAZADO" -> "Adelanto rechazado " + depositAmount;
            default -> "Adelanto pendiente " + depositAmount;
        };

        return status + " | " + depositLabel + " | Total " + total;
    }

    private String appointmentStatusLabel(String rawStatus) {
        String status = safe(rawStatus, "RESERVADO").toUpperCase(Locale.ROOT);
        return switch (status) {
            case "PENDING_DEPOSIT_VALIDATION" -> "Pendiente de validar adelanto";
            case "RESERVADO", "BOOKED", "CONFIRMED", "CONFIRMADO" -> "Reserva confirmada";
            case "CANCELLED", "CANCELED", "CANCELADO" -> "Reserva cancelada";
            case "COMPLETED", "COMPLETADO", "ATENDIDO" -> "Atencion completada";
            default -> "Reserva registrada";
        };
    }

    private String money(BigDecimal amount) {
        if (amount == null) {
            return "No informado";
        }
        return "S/ " + amount
                .setScale(2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    private String fullName(String firstName, String lastName, String fallback) {
        String value = (safe(firstName, "") + " " + safe(lastName, "")).trim();
        return value.isBlank() ? fallback : value;
    }

    private String safe(String value, String fallback) {
        if (value == null || value.trim().isBlank()) return fallback;
        return value.trim();
    }

    public record Payload(
            String appointmentId,
            String customerName,
            String customerPhone,
            String businessAndBranch,
            String serviceName,
            String professionalName,
            String date,
            String schedule,
            String paymentSummary,
            String agendaUrl
    ) {
    }
}
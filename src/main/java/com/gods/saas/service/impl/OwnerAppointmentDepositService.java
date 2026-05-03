package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.ValidateAppointmentDepositRequest;
import com.gods.saas.domain.dto.response.CreateAppointmentResponse;
import com.gods.saas.domain.model.Appointment;
import com.gods.saas.domain.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class OwnerAppointmentDepositService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Lima");

    private final AppointmentRepository appointmentRepository;

    @Transactional
    public CreateAppointmentResponse validateDeposit(
            Long tenantId,
            Long actorUserId,
            Long appointmentId,
            ValidateAppointmentDepositRequest request
    ) {
        Appointment appointment = appointmentRepository
                .findByIdAndTenant_Id(appointmentId, tenantId)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada"));

        if (!Boolean.TRUE.equals(appointment.getDepositRequired())) {
            throw new RuntimeException("Esta reserva no requiere pago inicial");
        }

        String currentStatus = appointment.getDepositStatus() == null
                ? ""
                : appointment.getDepositStatus().trim().toUpperCase();

        if ("PAID".equals(currentStatus)) {
            throw new RuntimeException("El pago inicial ya fue aprobado");
        }

        boolean approved = Boolean.TRUE.equals(request.getApproved());

        if (approved) {
            appointment.setDepositStatus("PAID");
            appointment.setEstado("CONFIRMED");
        } else {
            appointment.setDepositStatus("REJECTED");
            appointment.setEstado("DEPOSIT_REJECTED");
            if (request.getNote() != null && !request.getNote().isBlank()) {
                appointment.setDepositNote(request.getNote().trim());
            }
        }

        appointment.setDepositValidatedAt(LocalDateTime.now(BUSINESS_ZONE));
        appointment.setDepositValidatedByUserId(actorUserId);

        Appointment saved = appointmentRepository.save(appointment);

        return CreateAppointmentResponse.builder()
                .appointmentId(saved.getId())
                .estado(saved.getEstado())
                .build();
    }
}
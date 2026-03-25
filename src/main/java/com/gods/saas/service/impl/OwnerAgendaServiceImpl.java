package com.gods.saas.service.impl;


import com.gods.saas.domain.dto.response.OwnerAgendaResponse;
import com.gods.saas.domain.model.Appointment;
import com.gods.saas.domain.repository.AppointmentRepository;
import com.gods.saas.service.impl.impl.OwnerAgendaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OwnerAgendaServiceImpl implements OwnerAgendaService {

    private final AppointmentRepository appointmentRepository;

    @Override
    public List<OwnerAgendaResponse> getAgendaDelDia(Long tenantId, Long branchId, LocalDate fecha) {
        final List<Appointment> appointments =
                appointmentRepository.findByTenant_IdAndBranch_IdAndFechaOrderByHoraInicioAsc(
                        tenantId, branchId, fecha
                );

        final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        return appointments.stream()
                .map(a -> OwnerAgendaResponse.builder()
                        .appointmentId(a.getId())
                        .customerId(a.getCustomer() != null ? a.getCustomer().getId() : null)
                        .serviceId(a.getService() != null ? a.getService().getId() : null)
                        .barberUserId(a.getUser() != null ? a.getUser().getId() : null)
                        .fecha(a.getFecha() != null ? a.getFecha().toString() : null)
                        .hora(a.getHoraInicio() != null ? a.getHoraInicio().format(timeFormatter) : "")
                        .horaFin(a.getHoraFin() != null ? a.getHoraFin().format(timeFormatter) : "")
                        .cliente(a.getCustomer() != null ? a.getCustomer().getNombres() : "Cliente")
                        .telefono(a.getCustomer() != null ? a.getCustomer().getTelefono() : null)
                        .servicio(a.getService() != null ? a.getService().getNombre() : "Servicio")
                        .barbero(a.getUser() != null ? a.getUser().getNombre() : "Sin asignar")
                        .estado(a.getEstado() != null ? a.getEstado() : "RESERVADO")
                        .build()
                )
                .toList();
    }
}
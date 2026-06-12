package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.response.BarberAgendaItemResponse;
import com.gods.saas.domain.repository.AppointmentRepository;
import com.gods.saas.domain.repository.projection.BarberAgendaProjection;
import com.gods.saas.service.impl.impl.BarberAgendaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BarberAgendaServiceImpl implements BarberAgendaService {

    private final AppointmentRepository appointmentRepository;

    @Override
    public List<BarberAgendaItemResponse> getAgenda(Long tenantId, Long branchId, Long userId, LocalDate fecha) {
        final DateTimeFormatter hourFmt = DateTimeFormatter.ofPattern("HH:mm");

        List<BarberAgendaProjection> rows =
                appointmentRepository.findAgendaByTenantBranchUserAndFecha(
                        tenantId, branchId, userId, fecha
                );

        return rows.stream()
                .map(r -> {
                    System.out.println(
                            "BARBER AGENDA ROW => appointmentId=" + r.getAppointmentId()
                                    + ", customerId=" + r.getCustomerId()
                                    + ", cliente=" + r.getCliente()
                                    + ", servicio=" + r.getServicio()
                                    + ", estado=" + r.getEstado()
                                    + ", fecha=" + r.getFecha()
                                    + ", horaInicio=" + r.getHoraInicio()
                                    + ", horaFin=" + r.getHoraFin()
                                    + ", internalNote=" + r.getInternalNote()
                    );

                    String internalNote = cleanText(
                            firstText(
                                    r.getInternalNote(),
                                    r.getNotaInterna(),
                                    r.getNotes()
                            )
                    );

                    return BarberAgendaItemResponse.builder()
                            .appointmentId(r.getAppointmentId())
                            .customerId(r.getCustomerId())
                            .cliente(defaultText(r.getCliente(), "Cliente"))
                            .telefono(null)
                            .servicio(defaultText(r.getServicio(), "Servicio"))
                            .estado(defaultText(r.getEstado(), "RESERVADO"))
                            .fecha(r.getFecha() != null ? r.getFecha().toString() : null)
                            .hora(r.getHoraInicio() != null ? r.getHoraInicio().format(hourFmt) : "")
                            .horaFin(r.getHoraFin() != null ? r.getHoraFin().format(hourFmt) : null)
                            .internalNote(internalNote)
                            .notaInterna(internalNote)
                            .notes(internalNote)
                            .build();
                })
                .toList();
    }

    private String defaultText(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            String clean = cleanText(value);
            if (clean != null) {
                return clean;
            }
        }

        return null;
    }

    private String cleanText(String value) {
        if (value == null) {
            return null;
        }

        String clean = value.trim();
        if (clean.isEmpty() || "null".equalsIgnoreCase(clean)) {
            return null;
        }

        return clean;
    }
}

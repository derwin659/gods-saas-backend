package com.gods.saas.domain.repository.projection;

import java.time.LocalDate;
import java.time.LocalTime;

public interface BarberAgendaProjection {
    Long getAppointmentId();
    Long getCustomerId();
    String getCliente();
    String getTelefono();
    String getServicio();
    String getEstado();
    LocalDate getFecha();
    LocalTime getHoraInicio();
    LocalTime getHoraFin();

    // Nota interna creada por el dueño al agendar la cita.
    String getInternalNote();
    String getNotaInterna();
    String getNotes();
}

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
}

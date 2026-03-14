package com.gods.saas.domain.repository.projection;

import java.time.LocalDate;
import java.time.LocalTime;

public interface NextAppointmentProjection {
    Long getAppointmentId();
    LocalDate getFecha();
    LocalTime getHoraInicio();
    LocalTime getHoraFin();
    String getServicio();
    String getBarbero();
    String getBranch();
    String getEstado();
}
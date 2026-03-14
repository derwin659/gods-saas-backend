package com.gods.saas.domain.repository.projection;

import java.time.LocalDate;

public interface LastVisitProjection {
    Long getAppointmentId();
    LocalDate getFecha();
    String getServicio();
    Integer getPuntos();
    Double getTotal();
}
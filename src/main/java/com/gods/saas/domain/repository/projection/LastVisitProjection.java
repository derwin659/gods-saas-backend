package com.gods.saas.domain.repository.projection;

import java.time.LocalDate;

public interface LastVisitProjection {
    Long getAppointmentId();
    Long getSaleId();
    Boolean getReviewed();
    LocalDate getFecha();
    String getServicio();
    String getBarberPhotoUrl();
    String getBarbero();
    Integer getPuntos();
    Double getTotal();
}




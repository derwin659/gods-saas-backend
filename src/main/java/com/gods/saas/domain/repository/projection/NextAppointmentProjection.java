package com.gods.saas.domain.repository.projection;

import java.math.BigDecimal;
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
    Boolean getDepositRequired();
    BigDecimal getDepositAmount();
    BigDecimal getRemainingAmount();
    String getDepositStatus();
    String getDepositMethodCode();
    String getDepositMethodName();
    String getDepositOperationCode();
    String getDepositEvidenceUrl();
    String getDepositNote();
}

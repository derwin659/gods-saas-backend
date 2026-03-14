package com.gods.saas.domain.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface BarberCommissionDailyProjection {

    LocalDate getFecha();
    BigDecimal getVentas();
}

package com.gods.saas.domain.repository.projection;

import java.math.BigDecimal;

public interface CustomerHistorySaleItemProjection {
    Long getId();
    String getNombre();
    String getTipo();
    Integer getCantidad();
    BigDecimal getPrecioUnitario();
    BigDecimal getSubtotal();
    String getBarbero();
    String getBarberPhotoUrl();
}

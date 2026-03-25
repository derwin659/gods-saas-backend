package com.gods.saas.domain.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface BarberSaleDetailProjection {
    Long getSaleId();
    String getCustomerName();
    String getServiceNames();
    BigDecimal getTotal();
    String getPaymentMethod();
    LocalDateTime getCreatedAt();
}
package com.gods.saas.domain.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface CustomerReportProjection {
    Long getCustomerId();
    String getNombres();
    String getApellidos();
    String getTelefono();
    String getEmail();
    LocalDateTime getFechaRegistro();
    LocalDateTime getUltimaVisita();
    Long getBranchId();
    String getBranchName();
    Long getVisits();
    BigDecimal getTotalSpent();
    Integer getPuntos();
    Boolean getWhatsappTransactionalEnabled();
    Boolean getWhatsappMarketingEnabled();
    Boolean getWhatsappOptedOut();
}

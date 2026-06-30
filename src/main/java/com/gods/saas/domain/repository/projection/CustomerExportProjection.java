package com.gods.saas.domain.repository.projection;

import java.time.LocalDateTime;

public interface CustomerExportProjection {
    Long getCustomerId();
    String getNombres();
    String getApellidos();
    String getTelefono();
    String getEmail();
    LocalDateTime getFechaRegistro();
    LocalDateTime getUltimaVisita();
    String getSede();
    Integer getPuntos();
    Long getCompras();
    Boolean getActivo();
}

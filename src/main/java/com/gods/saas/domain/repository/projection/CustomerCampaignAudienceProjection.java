package com.gods.saas.domain.repository.projection;

import java.time.LocalDateTime;

public interface CustomerCampaignAudienceProjection {
    Long getCustomerId();
    String getNombre();
    String getTelefono();
    LocalDateTime getUltimaVisita();
}
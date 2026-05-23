package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OwnerTenantBrandingResponse {
    private Long tenantId;
    private String nombre;
    private String logoUrl;
    private String ciudad;
    private String businessType;
}

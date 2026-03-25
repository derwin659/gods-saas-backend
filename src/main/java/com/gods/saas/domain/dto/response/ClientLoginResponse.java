package com.gods.saas.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientLoginResponse {
    private Long customerId;
    private Long tenantId;
    private String tenantNombre;
    private String tenantLogoUrl;
    private Boolean phoneVerified;
    private Boolean appActivated;
}
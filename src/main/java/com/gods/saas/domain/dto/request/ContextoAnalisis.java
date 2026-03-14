package com.gods.saas.domain.dto.request;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContextoAnalisis {

    private long tenantId;        // tv | mobile | admin
    private long  sucursalId;    // futuro
}


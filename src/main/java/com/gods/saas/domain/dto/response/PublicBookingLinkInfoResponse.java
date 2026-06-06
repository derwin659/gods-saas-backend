package com.gods.saas.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicBookingLinkInfoResponse {

    private String codigoNegocio;
    private Long tenantId;
    private Long branchId;
    private Long barberId;

    private String tenantName;
    private String tenantLogoUrl;
    private String branchName;
    private String branchImageUrl;
    private String barberName;

    private String bookingLink;
}

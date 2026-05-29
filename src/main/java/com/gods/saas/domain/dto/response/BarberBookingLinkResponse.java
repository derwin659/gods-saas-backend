package com.gods.saas.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BarberBookingLinkResponse {

    private String bookingLink;

    private String codigoNegocio;
    private Long tenantId;
    private Long branchId;
    private Long barberId;

    private String tenantName;
    private String branchName;
    private String barberName;
}

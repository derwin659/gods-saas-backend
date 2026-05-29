package com.gods.saas.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerBookingLinksResponse {

    private String codigoNegocio;
    private Long tenantId;
    private String tenantName;
    private String businessLink;
    private List<BranchBookingLinkResponse> branches;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BranchBookingLinkResponse {
        private Long branchId;
        private String branchName;
        private String bookingLink;
    }
}

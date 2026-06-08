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

    private Long tenantId;
    private String tenantName;
    private String codigoNegocio;
    private String businessLink;
    private String walkInLink;
    private List<BranchBookingLink> branches;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BranchBookingLink {
        private Long branchId;
        private String branchName;
        private String bookingLink;
        private String walkInLink;
    }
}

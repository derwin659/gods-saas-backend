package com.gods.saas.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentMethodConfigResponse {
    private Long id;
    private Long branchId;

    private String code;
    private String displayName;
    private String countryCode;

    private String instructions;
    private String accountLabel;
    private String accountValue;
    private String qrImageUrl;

    private Boolean requiresOperationCode;
    private Boolean requiresEvidence;
    private Boolean active;

    private Integer sortOrder;
}
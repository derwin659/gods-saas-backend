package com.gods.saas.domain.dto.request;

import lombok.Data;

@Data
public class PaymentMethodConfigRequest {
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
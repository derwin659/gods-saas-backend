package com.gods.saas.domain.dto.response;

import com.gods.saas.domain.model.TenantPaymentMethod;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentMethodMiniResponse {
    private Long id;
    private String code;
    private String displayName;
    private String countryCode;
    private String instructions;
    private String accountLabel;
    private String accountValue;
    private String qrImageUrl;
    private Boolean requiresOperationCode;
    private Boolean requiresEvidence;

    public static PaymentMethodMiniResponse fromEntity(TenantPaymentMethod entity) {
        return PaymentMethodMiniResponse.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .displayName(entity.getDisplayName())
                .countryCode(entity.getCountryCode())
                .instructions(entity.getInstructions())
                .accountLabel(entity.getAccountLabel())
                .accountValue(entity.getAccountValue())
                .qrImageUrl(entity.getQrImageUrl())
                .requiresOperationCode(Boolean.TRUE.equals(entity.getRequiresOperationCode()))
                .requiresEvidence(Boolean.TRUE.equals(entity.getRequiresEvidence()))
                .build();
    }
}
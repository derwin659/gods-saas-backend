package com.gods.saas.domain.dto.response;

import com.gods.saas.domain.model.TenantPaymentMethod;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OwnerPaymentMethodResponse {
    private Long id;
    private Long branchId;
    private String code;
    private String displayName;
    private String countryCode;
    private Boolean active;
    private Integer sortOrder;

    public static OwnerPaymentMethodResponse base(String code, String displayName, Integer sortOrder) {
        return OwnerPaymentMethodResponse.builder()
                .id(null)
                .branchId(null)
                .code(code)
                .displayName(displayName)
                .countryCode(null)
                .active(true)
                .sortOrder(sortOrder == null ? 0 : sortOrder)
                .build();
    }

    public static OwnerPaymentMethodResponse fromEntity(TenantPaymentMethod method) {
        return OwnerPaymentMethodResponse.builder()
                .id(method.getId())
                .branchId(method.getBranch() != null ? method.getBranch().getId() : null)
                .code(method.getCode())
                .displayName(method.getDisplayName())
                .countryCode(method.getCountryCode())
                .active(method.getActive())
                .sortOrder(method.getSortOrder())
                .build();
    }
}

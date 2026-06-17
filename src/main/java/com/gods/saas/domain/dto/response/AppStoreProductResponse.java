package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AppStoreProductResponse {
    private String plan;
    private String productId;
    private String billingCycle;
}

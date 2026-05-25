package com.gods.saas.domain.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionPlanPriceResponse {
    private String plan;
    private String countryCode;
    private String currency;
    private Double monthlyAmount;
}

package com.gods.saas.domain.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionCheckoutResponse {
    private String provider;
    private String checkoutUrl;
    private String priceId;
    private String currency;
    private Double amount;
}
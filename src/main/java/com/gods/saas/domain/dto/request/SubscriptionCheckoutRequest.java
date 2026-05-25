package com.gods.saas.domain.dto.request;

import lombok.Data;

@Data
public class SubscriptionCheckoutRequest {
    private String plan;
    private String billingCycle;
}
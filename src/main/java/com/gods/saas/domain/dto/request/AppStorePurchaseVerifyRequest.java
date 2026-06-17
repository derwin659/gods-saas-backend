package com.gods.saas.domain.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AppStorePurchaseVerifyRequest {
    private String productId;
    private String receiptData;
    private String transactionId;
    private String originalTransactionId;
    private String appAccountToken;
}

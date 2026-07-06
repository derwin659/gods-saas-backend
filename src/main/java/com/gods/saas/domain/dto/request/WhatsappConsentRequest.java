package com.gods.saas.domain.dto.request;

import lombok.Data;

@Data
public class WhatsappConsentRequest {
    private Boolean transactionalEnabled;
    private Boolean marketingEnabled;
    private Boolean optedOut;
}
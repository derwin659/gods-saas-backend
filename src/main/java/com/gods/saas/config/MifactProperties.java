package com.gods.saas.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "electronic-invoicing.mifact")
public class MifactProperties {
    private boolean enabled = false;
    private String baseUrl = "https://demo.mifact.net.pe/api/invoiceService.svc";
    private int connectTimeoutSeconds = 10;
    private int readTimeoutSeconds = 30;
}

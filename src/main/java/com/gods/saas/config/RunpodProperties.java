package com.gods.saas.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "runpod")
public class RunpodProperties {

    private boolean enabled = true;
    private String apiKey;
    private String podId;
    private String apiBaseUrl = "https://rest.runpod.io/v1";
    private int inactivityMinutes = 15;
    private long healthPollIntervalMillis = 5000;
    private int healthTimeoutSeconds = 600;
}
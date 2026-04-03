package com.gods.saas.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "ia.ilustrativa")
public class IaIlustrativaProperties {

    private String url;
    private String healthPath = "/health";
    private String generatePath = "/generar";
    private int timeoutSeconds = 600;
}
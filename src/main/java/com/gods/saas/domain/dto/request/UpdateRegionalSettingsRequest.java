package com.gods.saas.domain.dto.request;

import lombok.Data;

@Data
public class UpdateRegionalSettingsRequest {
    private String language;
    private String preferredLocale;
    private String timezone;
    private String currency;
}

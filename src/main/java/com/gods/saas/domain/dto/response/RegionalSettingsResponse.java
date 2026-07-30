package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RegionalSettingsResponse {
    private String language;
    private String preferredLocale;
    private String effectiveLocale;
    private String timezone;
    private String currency;
    private String country;
    private List<LocaleOption> supportedLocales;

    public record LocaleOption(String code, String nativeName) {
    }
}

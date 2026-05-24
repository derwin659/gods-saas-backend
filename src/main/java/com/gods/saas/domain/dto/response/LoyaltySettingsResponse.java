package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class LoyaltySettingsResponse {
    private BigDecimal pointsPerCurrencyUnit;
    private String currency;
    private String currencySymbol;
}

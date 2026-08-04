package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class LoyaltySettingsResponse {
    private BigDecimal pointsPerCurrencyUnit;
    private String currency;
    private String currencySymbol;
    private Boolean welcomeBonusEnabled;
    private Integer welcomeBonusPoints;
    private Boolean activationBonusEnabled;
    private Integer activationBonusPoints;
    private List<LoyaltyTierConfig> tiers;
}

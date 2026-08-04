package com.gods.saas.domain.dto.request;

import com.gods.saas.domain.dto.response.LoyaltyTierConfig;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class UpdateLoyaltySettingsRequest {
    private BigDecimal pointsPerCurrencyUnit;
    private String currency;
    private Boolean welcomeBonusEnabled;
    private Integer welcomeBonusPoints;
    private Boolean activationBonusEnabled;
    private Integer activationBonusPoints;
    private List<LoyaltyTierConfig> tiers;
}

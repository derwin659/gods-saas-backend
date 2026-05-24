package com.gods.saas.domain.dto.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateLoyaltySettingsRequest {
    private BigDecimal pointsPerCurrencyUnit;
    private String currency;
}

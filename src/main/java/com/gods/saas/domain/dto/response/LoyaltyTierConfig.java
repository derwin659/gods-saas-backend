package com.gods.saas.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoyaltyTierConfig {
    private String id;
    private String name;
    private Integer minPoints;
    private String colorHex;
    private String iconName;
    private String description;
    private Boolean active;
}
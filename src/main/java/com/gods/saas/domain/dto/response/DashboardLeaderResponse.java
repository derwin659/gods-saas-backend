package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class DashboardLeaderResponse {
    private String name;
    private BigDecimal amount;
    private Long count;
}
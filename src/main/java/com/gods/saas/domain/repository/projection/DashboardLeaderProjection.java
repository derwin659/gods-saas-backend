package com.gods.saas.domain.repository.projection;

import java.math.BigDecimal;

public interface DashboardLeaderProjection {
    String getName();
    BigDecimal getAmount();
    Long getCount();
}
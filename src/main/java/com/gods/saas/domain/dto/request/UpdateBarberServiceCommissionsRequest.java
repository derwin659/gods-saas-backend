package com.gods.saas.domain.dto.request;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Map;

@Data
public class UpdateBarberServiceCommissionsRequest {
    private Map<Long, BigDecimal> servicePercentages;
}

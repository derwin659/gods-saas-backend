package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ServiceDeletionPreviewResponse {
    private Long serviceId;
    private String serviceName;
    private long appointments;
    private long saleItems;
    private long legacySaleDetails;
    private long localConsumptionItems;
    private long promotions;
    private long configurations;
    private boolean hasHistory;
    private String deletionMode;
    private String explanation;
}

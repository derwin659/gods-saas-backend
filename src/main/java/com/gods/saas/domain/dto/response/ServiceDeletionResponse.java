package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ServiceDeletionResponse {
    private Long serviceId;
    private String serviceName;
    private String deletionMode;
    private boolean deleted;
    private boolean historyPreserved;
    private String message;
}

package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BarberDeletionResponse {
    private Long barberUserId;
    private String barberName;
    private String deletionMode;
    private boolean deleted;
    private boolean historyPreserved;
    private String message;
}

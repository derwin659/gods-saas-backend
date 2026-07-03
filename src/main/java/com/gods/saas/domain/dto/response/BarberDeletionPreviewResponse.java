package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BarberDeletionPreviewResponse {
    private Long barberUserId;
    private String barberName;
    private long futureAppointments;
    private long historicalAppointments;
    private long saleItems;
    private long payments;
    private long advances;
    private long schedules;
    private long configurations;
    private boolean hasHistory;
    private boolean blocked;
    private String deletionMode;
    private String explanation;
}

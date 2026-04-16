package com.gods.saas.domain.dto.request;

import lombok.Data;

@Data
public class CreateBarberTimeBlockRequest {
    private Long barberUserId;
    private String blockDate;
    private String startTime;
    private String endTime;
    private Boolean allDay;
    private String reason;
}

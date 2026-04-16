package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BarberTimeBlockResponse {
    private Long id;
    private Long barberUserId;
    private String blockDate;
    private String startTime;
    private String endTime;
    private Boolean allDay;
    private String reason;
}
package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BarberAvailabilityDayResponse {
    private Integer dayOfWeek;
    private Boolean isWorking;
    private String startTime;
    private String endTime;
}
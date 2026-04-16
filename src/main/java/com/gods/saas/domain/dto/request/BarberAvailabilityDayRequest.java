package com.gods.saas.domain.dto.request;

import lombok.Data;

import java.time.LocalTime;

@Data
public class BarberAvailabilityDayRequest {
    private Integer dayOfWeek;
    private Boolean isWorking;
    private LocalTime startTime;
    private LocalTime endTime;
}
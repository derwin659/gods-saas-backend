package com.gods.saas.domain.dto.request;


import lombok.Data;

import java.util.List;

@Data
public class SaveBarberAvailabilityRequest {
    private Long barberUserId;
    private List<BarberAvailabilityDayRequest> days;
}
package com.gods.saas.domain.dto.response;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UpcomingAppointmentItemResponse {
    private Long appointmentId;
    private String time;
    private String customerName;
    private String serviceName;
    private String barberName;
}
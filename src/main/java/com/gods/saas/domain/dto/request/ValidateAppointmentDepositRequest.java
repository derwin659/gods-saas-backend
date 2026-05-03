package com.gods.saas.domain.dto.request;

import lombok.Data;

@Data
public class ValidateAppointmentDepositRequest {
    private Boolean approved;
    private String note;
}
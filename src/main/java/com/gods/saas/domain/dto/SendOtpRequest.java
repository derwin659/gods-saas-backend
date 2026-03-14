package com.gods.saas.domain.dto;

import lombok.Data;

@Data
public class SendOtpRequest {
    private String phone;
    private Long tenantId;
    private Long branchId;
}





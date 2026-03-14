package com.gods.saas.domain.dto;

import lombok.Data;

@Data
public class VerifyOtpRequest {
    private String phone;
    private Long tenantId;
    private String code;
}
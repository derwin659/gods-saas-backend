package com.gods.saas.domain.dto.request;

import lombok.Data;

@Data
public class OtpRequest {
    private Long tenantId;
    private String phone;
}

package com.gods.saas.domain.dto.request;

import lombok.Data;

@Data
public class OtpVerifyRequest {
    private Long otpId;
    private String code;
}

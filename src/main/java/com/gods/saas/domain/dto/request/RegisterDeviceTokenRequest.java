package com.gods.saas.domain.dto.request;

import lombok.Data;

@Data
public class RegisterDeviceTokenRequest {
    private String token;
    private String platform;
}
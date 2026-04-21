package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeviceTokenResponse {
    private Long id;
    private String token;
    private String platform;
    private Boolean active;
    private String message;
}
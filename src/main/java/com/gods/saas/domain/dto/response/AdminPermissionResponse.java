package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminPermissionResponse {
    private String permissionKey;
    private Boolean enabled;
}
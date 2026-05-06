package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AdminPermissionsBundleResponse {
    private Long userId;
    private Long tenantId;
    private String role;
    private Boolean owner;
    private List<String> permissions;
}
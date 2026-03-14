package com.gods.saas.domain.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TenantUserDto {
    private Long id;
    private String name;
    private String email;
    private String role;
    private boolean active;
    private String photoUrl;
    private String createdAt;
}


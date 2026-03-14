package com.gods.saas.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class TenantDto {
    private Long id;
    private String name;
    private String country;
    private String city;
    private String ownerName;
    private String plan;
    private String status;
    private String codigo;
    private String createdAt;
}

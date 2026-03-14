package com.gods.saas.domain.dto;

import lombok.Data;

@Data
public class TenantPayloadDto {
    private String name;
    private String country;
    private String city;
    private String ownerName;
    private String plan;
}


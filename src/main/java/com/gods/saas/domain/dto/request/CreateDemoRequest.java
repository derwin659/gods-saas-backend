package com.gods.saas.domain.dto.request;

import com.gods.saas.domain.enums.BusinessType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateDemoRequest {

    private String businessName;
    private BusinessType businessType;

    private String ownerName;
    private String ownerEmail;
    private String ownerPhone;

    private String country;
    private String city;

    private Integer branchesCount;
    private Integer professionalsCount;

    private String socialLink;
    private String googleMapsLink;
    private String message;
}
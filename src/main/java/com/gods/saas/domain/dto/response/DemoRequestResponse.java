package com.gods.saas.domain.dto.response;

import com.gods.saas.domain.enums.BusinessType;
import com.gods.saas.domain.enums.DemoRequestStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class DemoRequestResponse {

    private Long id;

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

    private DemoRequestStatus status;
    private String reviewNotes;
    private Long reviewedBy;
    private Long createdTenantId;

    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
}
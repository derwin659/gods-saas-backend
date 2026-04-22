package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MarketingCampaignResponse {
    private Long id;
    private String code;
    private String name;
    private boolean enabled;
    private boolean channelPush;
    private boolean channelInApp;
    private boolean channelWhatsapp;
    private String customTitle;
    private String customMessage;
    private Integer daysInactive;
}
package com.gods.saas.domain.dto.request;

import lombok.Data;

@Data
public class MarketingCampaignRequest {
    private String code;
    private String name;
    private Boolean enabled;
    private Boolean channelPush;
    private Boolean channelInApp;
    private Boolean channelWhatsapp;
    private String customTitle;
    private String customMessage;
    private Integer daysInactive;
}
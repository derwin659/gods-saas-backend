package com.gods.saas.domain.dto.request;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class SegmentCampaignRunRequest {
    private Boolean confirmed;
    private String title;
    private String message;
    private Boolean channelWhatsapp;
    private List<Long> customerIds;
    private Map<String, Object> filterSnapshot;
}
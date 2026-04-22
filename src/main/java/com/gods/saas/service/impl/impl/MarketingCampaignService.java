package com.gods.saas.service.impl.impl;

import com.gods.saas.domain.dto.request.MarketingCampaignRequest;
import com.gods.saas.domain.dto.response.MarketingCampaignResponse;

import java.util.List;

public interface MarketingCampaignService {

    List<MarketingCampaignResponse> findAll(Long tenantId);

    MarketingCampaignResponse create(Long tenantId, MarketingCampaignRequest request);

    MarketingCampaignResponse update(Long tenantId, Long campaignId, MarketingCampaignRequest request);

    MarketingCampaignResponse toggle(Long tenantId, Long campaignId);

    void delete(Long tenantId, Long campaignId);
}
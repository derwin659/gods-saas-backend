package com.gods.saas.service.impl;

import com.gods.saas.domain.model.MarketingCampaign;
import com.gods.saas.domain.repository.projection.CustomerCampaignAudienceProjection;
import org.springframework.stereotype.Service;

@Service
public class CampaignMessageBuilderService {

    public String buildTitle(MarketingCampaign campaign) {
        if (campaign.getCustomTitle() != null && !campaign.getCustomTitle().isBlank()) {
            return campaign.getCustomTitle().trim();
        }

        return switch (campaign.getCode()) {
            case "INACTIVE_15" -> "Te extrañamos en tu barbería";
            case "INACTIVE_30" -> "Vuelve a visitarnos";
            default -> "Tenemos algo para ti";
        };
    }

    public String buildMessage(MarketingCampaign campaign, CustomerCampaignAudienceProjection customer) {
        if (campaign.getCustomMessage() != null && !campaign.getCustomMessage().isBlank()) {
            return campaign.getCustomMessage().trim();
        }

        return switch (campaign.getCode()) {
            case "INACTIVE_15" ->
                    "Han pasado algunos días desde tu última visita. Reserva esta semana y vuelve a lucir increíble 💈";
            case "INACTIVE_30" ->
                    "Hace tiempo no vienes. Tenemos un beneficio especial para tu próxima visita. Te esperamos 💈";
            default ->
                    "Tenemos una campaña especial para ti.";
        };
    }
}
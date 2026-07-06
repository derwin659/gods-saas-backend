package com.gods.saas.service.impl;

import com.gods.saas.domain.model.Customer;
import com.gods.saas.domain.model.MarketingCampaign;
import com.gods.saas.domain.model.MarketingCampaignDelivery;
import com.gods.saas.domain.repository.CustomerRepository;
import com.gods.saas.domain.repository.MarketingCampaignDeliveryRepository;
import com.gods.saas.domain.repository.MarketingCampaignRepository;
import com.gods.saas.domain.repository.SaleRepository;
import com.gods.saas.domain.repository.projection.CustomerCampaignAudienceProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CampaignOperationsService {
    private final MarketingCampaignRepository campaignRepository;
    private final MarketingCampaignDeliveryRepository deliveryRepository;
    private final SaleRepository saleRepository;
    private final CustomerRepository customerRepository;
    private final MarketingCampaignProcessorService processorService;

    @Transactional(readOnly = true)
    public Map<String, Object> preview(Long tenantId) {
        int audience = 0;
        int eligible = 0;
        int withoutConsent = 0;
        int cooldown = 0;
        int campaigns = 0;

        for (MarketingCampaign campaign : campaignRepository.findByTenant_IdAndEnabledTrue(tenantId)) {
            if (!supported(campaign) || campaign.getDaysInactive() == null || campaign.getDaysInactive() <= 0) continue;
            campaigns++;
            List<CustomerCampaignAudienceProjection> customers =
                    saleRepository.findInactiveCustomers(tenantId, campaign.getDaysInactive());
            audience += customers.size();
            for (CustomerCampaignAudienceProjection projection : customers) {
                Customer customer = customerRepository.findById(projection.getCustomerId()).orElse(null);
                if (customer == null) continue;
                if (campaign.isChannelWhatsapp()
                        && (!Boolean.TRUE.equals(customer.getWhatsappMarketingEnabled())
                        || customer.getWhatsappOptedOutAt() != null)) {
                    withoutConsent++;
                } else if (deliveryRepository.existsRecently(
                        tenantId, customer.getId(), campaign.getCode(),
                        LocalDateTime.now().minusDays(cooldownDays(campaign)))) {
                    cooldown++;
                } else {
                    eligible++;
                }
            }
        }
        return Map.of(
                "campaigns", campaigns, "audience", audience, "eligible", eligible,
                "withoutConsent", withoutConsent, "cooldown", cooldown,
                "requiresConfirmation", true);
    }

    @Transactional
    public Map<String, Object> run(Long tenantId, boolean confirmed) {
        if (!confirmed) throw new IllegalArgumentException("Debes confirmar el envio");
        long before = deliveryRepository.countByTenant_Id(tenantId);
        processorService.processTenantCampaigns(tenantId);
        long sent = deliveryRepository.countByTenant_Id(tenantId) - before;
        return Map.of("sent", sent, "message", "Campanas ejecutadas correctamente");
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> history(Long tenantId) {
        return deliveryRepository.findTop100ByTenant_IdOrderBySentAtDesc(tenantId).stream()
                .map(this::historyItem).toList();
    }

    private Map<String, Object> historyItem(MarketingCampaignDelivery delivery) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", delivery.getId());
        item.put("campaignName", delivery.getMarketingCampaign().getName());
        item.put("campaignCode", delivery.getCampaignCode());
        item.put("customerName", customerName(delivery.getCustomer()));
        item.put("sentAt", delivery.getSentAt());
        return item;
    }

    private boolean supported(MarketingCampaign campaign) {
        return "INACTIVE_15".equalsIgnoreCase(campaign.getCode())
                || "INACTIVE_30".equalsIgnoreCase(campaign.getCode());
    }

    private int cooldownDays(MarketingCampaign campaign) {
        return "INACTIVE_15".equalsIgnoreCase(campaign.getCode()) ? 10 : 15;
    }

    private String customerName(Customer customer) {
        String value = ((customer.getNombres() == null ? "" : customer.getNombres()) + " "
                + (customer.getApellidos() == null ? "" : customer.getApellidos())).trim();
        return value.isBlank() ? "Cliente" : value;
    }
}

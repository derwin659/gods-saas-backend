package com.gods.saas.service.impl;

import com.gods.saas.domain.enums.NotificationChannel;
import com.gods.saas.domain.enums.NotificationDeliveryStatus;
import com.gods.saas.domain.enums.NotificationType;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import com.gods.saas.domain.repository.projection.CustomerCampaignAudienceProjection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class MarketingCampaignProcessorService {

    private final SubscriptionRepository subscriptionRepository;
    private final MarketingCampaignRepository marketingCampaignRepository;
    private final MarketingCampaignDeliveryRepository marketingCampaignDeliveryRepository;
    private final SaleRepository saleRepository;
    private final TenantRepository tenantRepository;
    private final CustomerRepository customerRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryRepository notificationDeliveryRepository;
    private final CampaignMessageBuilderService campaignMessageBuilderService;

    public void processAllEligibleTenants() {
        List<Tenant> tenants = tenantRepository.findAll();

        for (Tenant tenant : tenants) {
            if (!isProOrGodsAi(tenant.getId())) {
                continue;
            }

            processTenantCampaigns(tenant.getId());
        }
    }

    public void processTenantCampaigns(Long tenantId) {
        List<MarketingCampaign> campaigns = marketingCampaignRepository.findByTenant_IdAndEnabledTrue(tenantId);

        for (MarketingCampaign campaign : campaigns) {
            if ("INACTIVE_15".equalsIgnoreCase(campaign.getCode())) {
                processInactiveCampaign(campaign, 10);
            } else if ("INACTIVE_30".equalsIgnoreCase(campaign.getCode())) {
                processInactiveCampaign(campaign, 15);
            }
        }
    }

    private void processInactiveCampaign(MarketingCampaign campaign, int cooldownDays) {
        Long tenantId = campaign.getTenant().getId();
        Integer daysInactive = campaign.getDaysInactive();

        if (daysInactive == null || daysInactive <= 0) {
            log.warn("CAMPAIGN SKIPPED => campaignId={}, reason=daysInactive inválido", campaign.getId());
            return;
        }

        List<CustomerCampaignAudienceProjection> customers =
                saleRepository.findInactiveCustomers(tenantId, daysInactive);

        log.info("CAMPAIGN PROCESS => tenantId={}, code={}, audience={}",
                tenantId, campaign.getCode(), customers.size());

        for (CustomerCampaignAudienceProjection customerProjection : customers) {
            Long customerId = customerProjection.getCustomerId();

            boolean alreadySentRecently =
                    marketingCampaignDeliveryRepository.existsRecently(
                            tenantId,
                            customerId,
                            campaign.getCode(),
                            LocalDateTime.now().minusDays(cooldownDays)
                    );

            if (alreadySentRecently) {
                continue;
            }

            Customer customer = customerRepository.findById(customerId)
                    .orElse(null);

            if (customer == null) {
                continue;
            }

            createCampaignNotification(campaign, customer, customerProjection);
            registerCampaignDelivery(campaign, customer);
        }
    }

    private void createCampaignNotification(
            MarketingCampaign campaign,
            Customer customer,
            CustomerCampaignAudienceProjection projection
    ) {
        NotificationType type = mapNotificationType(campaign.getCode());

        Notification notification = Notification.builder()
                .tenant(campaign.getTenant())
                .branch(null)
                .customer(customer)
                .user(null)
                .type(type)
                .title(campaignMessageBuilderService.buildTitle(campaign))
                .message(campaignMessageBuilderService.buildMessage(campaign, projection))
                .referenceType("MARKETING_CAMPAIGN")
                .referenceId(campaign.getId())
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();

        notificationRepository.save(notification);

        if (campaign.isChannelInApp()) {
            notificationDeliveryRepository.save(
                    NotificationDelivery.builder()
                            .notification(notification)
                            .channel(NotificationChannel.IN_APP)
                            .status(NotificationDeliveryStatus.SENT)
                            .attempts(0)
                            .sentAt(LocalDateTime.now())
                            .createdAt(LocalDateTime.now())
                            .build()
            );
        }

        if (campaign.isChannelPush()) {
            notificationDeliveryRepository.save(
                    NotificationDelivery.builder()
                            .notification(notification)
                            .channel(NotificationChannel.PUSH)
                            .status(NotificationDeliveryStatus.PENDING)
                            .attempts(0)
                            .createdAt(LocalDateTime.now())
                            .build()
            );
        }

        if (campaign.isChannelWhatsapp()) {
            notificationDeliveryRepository.save(
                    NotificationDelivery.builder()
                            .notification(notification)
                            .channel(NotificationChannel.WHATSAPP)
                            .status(NotificationDeliveryStatus.PENDING)
                            .attempts(0)
                            .createdAt(LocalDateTime.now())
                            .build()
            );
        }
    }

    private void registerCampaignDelivery(MarketingCampaign campaign, Customer customer) {
        marketingCampaignDeliveryRepository.save(
                MarketingCampaignDelivery.builder()
                        .marketingCampaign(campaign)
                        .tenant(campaign.getTenant())
                        .customer(customer)
                        .campaignCode(campaign.getCode())
                        .sentAt(LocalDateTime.now())
                        .build()
        );
    }

    private NotificationType mapNotificationType(String code) {
        return switch (code.toUpperCase(Locale.ROOT)) {
            case "INACTIVE_15" -> NotificationType.CAMPAIGN_INACTIVE_15;
            case "INACTIVE_30" -> NotificationType.CAMPAIGN_INACTIVE_30;
            default -> throw new IllegalArgumentException("Código de campaña no soportado: " + code);
        };
    }

    private boolean isProOrGodsAi(Long tenantId) {
        Subscription subscription = subscriptionRepository
                .findTopByTenantIdOrderByFechaInicioDesc(tenantId)
                .orElse(null);

        if (subscription == null || subscription.getPlan() == null) {
            return false;
        }

        String plan = subscription.getPlan().trim().toUpperCase(Locale.ROOT);

        return "PRO".equals(plan)
                || "GODS_AI".equals(plan)
                || "GODS AI".equals(plan);
    }
}
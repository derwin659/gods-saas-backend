package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.SegmentCampaignRunRequest;
import com.gods.saas.domain.enums.NotificationChannel;
import com.gods.saas.domain.enums.NotificationDeliveryStatus;
import com.gods.saas.domain.enums.NotificationType;
import com.gods.saas.domain.model.Customer;
import com.gods.saas.domain.model.MarketingCampaign;
import com.gods.saas.domain.model.MarketingCampaignDelivery;
import com.gods.saas.domain.model.Notification;
import com.gods.saas.domain.model.NotificationDelivery;
import com.gods.saas.domain.model.Tenant;
import com.gods.saas.domain.repository.CustomerRepository;
import com.gods.saas.domain.repository.MarketingCampaignDeliveryRepository;
import com.gods.saas.domain.repository.MarketingCampaignRepository;
import com.gods.saas.domain.repository.NotificationDeliveryRepository;
import com.gods.saas.domain.repository.NotificationRepository;
import com.gods.saas.domain.repository.SaleRepository;
import com.gods.saas.domain.repository.projection.CustomerCampaignAudienceProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CampaignOperationsService {
    private static final String SEGMENT_CAMPAIGN_CODE = "SEGMENT_MANUAL";

    private final MarketingCampaignRepository campaignRepository;
    private final MarketingCampaignDeliveryRepository deliveryRepository;
    private final SaleRepository saleRepository;
    private final CustomerRepository customerRepository;
    private final MarketingCampaignProcessorService processorService;
    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryRepository notificationDeliveryRepository;

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

    @Transactional
    public Map<String, Object> runSegment(Long tenantId, Long actorUserId, SegmentCampaignRunRequest request) {
        if (request == null || !Boolean.TRUE.equals(request.getConfirmed())) {
            throw new IllegalArgumentException("Debes confirmar el envio de la campana segmentada");
        }

        List<Long> customerIds = sanitizeIds(request.getCustomerIds());
        if (customerIds.isEmpty()) {
            throw new IllegalArgumentException("Selecciona al menos un cliente para la campana");
        }
        if (customerIds.size() > 500) {
            throw new IllegalArgumentException("La audiencia maxima por campana segmentada es de 500 clientes");
        }

        String title = clean(request.getTitle(), "Tenemos algo especial para ti", 150);
        String message = clean(request.getMessage(), "Hola, tenemos una campana especial para ti. Responde este mensaje para coordinar tu visita.", 500);
        boolean channelWhatsapp = request.getChannelWhatsapp() == null || Boolean.TRUE.equals(request.getChannelWhatsapp());
        String filterSnapshot = limit(String.valueOf(request.getFilterSnapshot() == null ? Map.of() : request.getFilterSnapshot()), 2000);
        MarketingCampaign campaign = resolveSegmentCampaign(tenantId, title, message, channelWhatsapp);

        int requested = customerIds.size();
        int queued = 0;
        int withoutConsent = 0;
        int noPhone = 0;
        int cooldown = 0;
        int notFound = 0;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cooldownSince = now.minusDays(7);

        for (Long customerId : customerIds) {
            Customer customer = customerRepository.findById(customerId).orElse(null);
            if (customer == null || customer.getTenant() == null || !tenantId.equals(customer.getTenant().getId())) {
                notFound++;
                continue;
            }

            String phone = customer.getTelefono() == null ? "" : customer.getTelefono().trim();
            if (channelWhatsapp && phone.isBlank()) {
                noPhone++;
                continue;
            }

            if (channelWhatsapp && (!Boolean.TRUE.equals(customer.getWhatsappMarketingEnabled()) || customer.getWhatsappOptedOutAt() != null)) {
                withoutConsent++;
                continue;
            }

            if (deliveryRepository.existsRecently(tenantId, customer.getId(), SEGMENT_CAMPAIGN_CODE, cooldownSince)) {
                cooldown++;
                continue;
            }

            Notification notification = notificationRepository.save(Notification.builder()
                    .tenant(campaign.getTenant())
                    .branch(null)
                    .customer(customer)
                    .user(null)
                    .type(NotificationType.CAMPAIGN_SEGMENT)
                    .title(title)
                    .message(message)
                    .referenceType("MARKETING_CAMPAIGN")
                    .referenceId(campaign.getId())
                    .isRead(false)
                    .createdAt(now)
                    .build());

            notificationDeliveryRepository.save(NotificationDelivery.builder()
                    .notification(notification)
                    .channel(channelWhatsapp ? NotificationChannel.WHATSAPP : NotificationChannel.IN_APP)
                    .status(channelWhatsapp ? NotificationDeliveryStatus.PENDING : NotificationDeliveryStatus.SENT)
                    .attempts(0)
                    .sentAt(channelWhatsapp ? null : now)
                    .createdAt(now)
                    .build());

            deliveryRepository.save(MarketingCampaignDelivery.builder()
                    .marketingCampaign(campaign)
                    .tenant(campaign.getTenant())
                    .customer(customer)
                    .campaignCode(SEGMENT_CAMPAIGN_CODE)
                    .actorUserId(actorUserId)
                    .title(title)
                    .message(message)
                    .channelWhatsapp(channelWhatsapp)
                    .deliveryStatus(channelWhatsapp ? "PENDING" : "SENT")
                    .phone(phone)
                    .filterSnapshot(filterSnapshot)
                    .sentAt(now)
                    .build());
            queued++;
        }

        return Map.of(
                "requested", requested,
                "queued", queued,
                "sent", queued,
                "withoutConsent", withoutConsent,
                "noPhone", noPhone,
                "cooldown", cooldown,
                "notFound", notFound,
                "message", "Campana segmentada registrada correctamente"
        );
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
        item.put("title", delivery.getTitle());
        item.put("message", delivery.getMessage());
        item.put("channelWhatsapp", delivery.getChannelWhatsapp());
        item.put("deliveryStatus", delivery.getDeliveryStatus());
        item.put("phone", delivery.getPhone());
        item.put("actorUserId", delivery.getActorUserId());
        return item;
    }

    private MarketingCampaign resolveSegmentCampaign(Long tenantId, String title, String message, boolean channelWhatsapp) {
        return campaignRepository.findByTenant_IdAndCode(tenantId, SEGMENT_CAMPAIGN_CODE)
                .orElseGet(() -> campaignRepository.save(MarketingCampaign.builder()
                        .tenant(Tenant.builder().id(tenantId).build())
                        .code(SEGMENT_CAMPAIGN_CODE)
                        .name("Campanas segmentadas")
                        .enabled(false)
                        .channelPush(false)
                        .channelInApp(!channelWhatsapp)
                        .channelWhatsapp(channelWhatsapp)
                        .customTitle(title)
                        .customMessage(message)
                        .daysInactive(null)
                        .createdAt(LocalDateTime.now())
                        .build()));
    }

    private List<Long> sanitizeIds(List<Long> raw) {
        if (raw == null) return List.of();
        return raw.stream().filter(Objects::nonNull).distinct().limit(501).toList();
    }

    private String clean(String value, String fallback, int max) {
        String result = value == null || value.isBlank() ? fallback : value.trim();
        return limit(result, max);
    }

    private String limit(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
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
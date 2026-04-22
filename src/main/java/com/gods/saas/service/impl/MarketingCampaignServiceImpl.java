package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.MarketingCampaignRequest;
import com.gods.saas.domain.dto.response.MarketingCampaignResponse;
import com.gods.saas.domain.model.MarketingCampaign;
import com.gods.saas.domain.model.Tenant;
import com.gods.saas.domain.repository.MarketingCampaignRepository;
import com.gods.saas.domain.repository.TenantRepository;
import com.gods.saas.service.impl.impl.MarketingCampaignService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional
public class MarketingCampaignServiceImpl implements MarketingCampaignService {

    private final MarketingCampaignRepository marketingCampaignRepository;
    private final TenantRepository tenantRepository;

    @Override
    @Transactional(readOnly = true)
    public List<MarketingCampaignResponse> findAll(Long tenantId) {
        return marketingCampaignRepository.findByTenant_IdOrderByCreatedAtDesc(tenantId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public MarketingCampaignResponse create(Long tenantId, MarketingCampaignRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant no encontrado"));

        validateRequest(request);

        String code = normalizeCode(request.getCode());

        if (marketingCampaignRepository.existsByTenant_IdAndCode(tenantId, code)) {
            throw new IllegalArgumentException("Ya existe una campaña con ese código");
        }

        MarketingCampaign entity = MarketingCampaign.builder()
                .tenant(tenant)
                .code(code)
                .name(trimOrDefault(request.getName(), code))
                .enabled(Boolean.TRUE.equals(request.getEnabled()))
                .channelPush(defaultTrue(request.getChannelPush()))
                .channelInApp(defaultTrue(request.getChannelInApp()))
                .channelWhatsapp(Boolean.TRUE.equals(request.getChannelWhatsapp()))
                .customTitle(trimToNull(request.getCustomTitle()))
                .customMessage(trimToNull(request.getCustomMessage()))
                .daysInactive(request.getDaysInactive())
                .createdAt(LocalDateTime.now())
                .build();

        marketingCampaignRepository.save(entity);
        return toResponse(entity);
    }

    @Override
    public MarketingCampaignResponse update(Long tenantId, Long campaignId, MarketingCampaignRequest request) {
        MarketingCampaign entity = marketingCampaignRepository.findByIdAndTenant_Id(campaignId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Campaña no encontrada"));

        validateRequest(request);

        if (request.getCode() != null && !request.getCode().isBlank()) {
            String newCode = normalizeCode(request.getCode());
            if (!newCode.equalsIgnoreCase(entity.getCode())
                    && marketingCampaignRepository.existsByTenant_IdAndCode(tenantId, newCode)) {
                throw new IllegalArgumentException("Ya existe una campaña con ese código");
            }
            entity.setCode(newCode);
        }

        entity.setName(trimOrDefault(request.getName(), entity.getName()));
        if (request.getEnabled() != null) entity.setEnabled(request.getEnabled());
        if (request.getChannelPush() != null) entity.setChannelPush(request.getChannelPush());
        if (request.getChannelInApp() != null) entity.setChannelInApp(request.getChannelInApp());
        if (request.getChannelWhatsapp() != null) entity.setChannelWhatsapp(request.getChannelWhatsapp());

        entity.setCustomTitle(trimToNull(request.getCustomTitle()));
        entity.setCustomMessage(trimToNull(request.getCustomMessage()));
        entity.setDaysInactive(request.getDaysInactive());

        marketingCampaignRepository.save(entity);
        return toResponse(entity);
    }

    @Override
    public MarketingCampaignResponse toggle(Long tenantId, Long campaignId) {
        MarketingCampaign entity = marketingCampaignRepository.findByIdAndTenant_Id(campaignId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Campaña no encontrada"));

        entity.setEnabled(!entity.isEnabled());
        marketingCampaignRepository.save(entity);

        return toResponse(entity);
    }

    @Override
    public void delete(Long tenantId, Long campaignId) {
        MarketingCampaign entity = marketingCampaignRepository.findByIdAndTenant_Id(campaignId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Campaña no encontrada"));

        marketingCampaignRepository.delete(entity);
    }

    private void validateRequest(MarketingCampaignRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("El nombre es obligatorio");
        }

        if (request.getCode() == null || request.getCode().isBlank()) {
            throw new IllegalArgumentException("El código es obligatorio");
        }

        if (request.getDaysInactive() != null && request.getDaysInactive() < 0) {
            throw new IllegalArgumentException("Los días de inactividad no pueden ser negativos");
        }
    }

    private MarketingCampaignResponse toResponse(MarketingCampaign entity) {
        return MarketingCampaignResponse.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .name(entity.getName())
                .enabled(entity.isEnabled())
                .channelPush(entity.isChannelPush())
                .channelInApp(entity.isChannelInApp())
                .channelWhatsapp(entity.isChannelWhatsapp())
                .customTitle(entity.getCustomTitle())
                .customMessage(entity.getCustomMessage())
                .daysInactive(entity.getDaysInactive())
                .build();
    }

    private String normalizeCode(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String trimOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean defaultTrue(Boolean value) {
        return value == null || value;
    }
}
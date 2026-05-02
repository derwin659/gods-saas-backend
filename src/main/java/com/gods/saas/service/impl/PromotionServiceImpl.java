package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.PromotionRequest;
import com.gods.saas.domain.dto.response.ClientPromotionResponse;
import com.gods.saas.domain.dto.response.PromotionResponse;
import com.gods.saas.domain.enums.PromotionRedirectType;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import com.gods.saas.service.impl.impl.NotificationService;
import com.gods.saas.service.impl.impl.PromotionService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class PromotionServiceImpl implements PromotionService {

    private final SubscriptionRepository subscriptionRepository;
    private final CustomerRepository customerRepository;
    private final LoyaltyAccountRepository loyaltyAccountRepository;
    private final PromotionRepository promotionRepository;
    private final TenantRepository tenantRepository;
    private final BranchRepository branchRepository;
    private final NotificationService notificationService;
    private final CloudinaryStorageService cloudinaryStorageService;

    @Override
    @Transactional(readOnly = true)
    public List<ClientPromotionResponse> getClientPromotions(String idCustomer) {
        Customer customer = customerRepository.findById(Long.parseLong(idCustomer))
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));

        Long tenantId = customer.getTenant().getId();

        LoyaltyAccount loyalty = loyaltyAccountRepository
                .findByTenant_IdAndCustomer_Id(tenantId, customer.getId())
                .orElse(null);

        int puntosDisponibles = loyalty != null && loyalty.getPuntosDisponibles() != null
                ? loyalty.getPuntosDisponibles()
                : 0;

        List<Promotion> promotions = promotionRepository.findActiveClientPromotions(
                tenantId,
                puntosDisponibles
        );

        return promotions.stream()
                .map(this::toClientResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromotionResponse> getOwnerPromotions(Long tenantId) {
        return promotionRepository.findByTenant_IdOrderByOrdenVisualAscCreatedAtDesc(tenantId)
                .stream()
                .map(this::toOwnerResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PromotionResponse getOwnerPromotionById(Long tenantId, Long promotionId) {
        Promotion promotion = promotionRepository.findByIdAndTenant_Id(promotionId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Promoción no encontrada"));
        return toOwnerResponse(promotion);
    }

    @Override
    public PromotionResponse createPromotion(Long tenantId, PromotionRequest request) {
        validatePromotionsCreateAllowed(tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant no encontrado"));

        Branch branch = resolveBranch(tenantId, request.getBranchId());

        Promotion promotion = Promotion.builder()
                .tenant(tenant)
                .branch(branch)
                .titulo(trim(request.getTitulo()))
                .subtitulo(trim(request.getSubtitulo()))
                .descripcion(trim(request.getDescripcion()))
                .tipo(request.getTipo())
                .badge(trim(request.getBadge()))
                .imageUrl(trim(request.getImageUrl()))
                .iconName(trim(request.getIconName()))
                .priceText(trim(request.getPriceText()))
                .ctaLabel(trim(request.getCtaLabel()))
                .redirectType(request.getRedirectType() != null ? request.getRedirectType() : PromotionRedirectType.NONE)
                .redirectValue(trim(request.getRedirectValue()))
                .destacado(Boolean.TRUE.equals(request.getDestacado()))
                .soloClientesConPuntos(Boolean.TRUE.equals(request.getSoloClientesConPuntos()))
                .puntosMinimos(request.getPuntosMinimos())
                .activo(request.getActivo() == null || request.getActivo())
                .fechaInicio(request.getFechaInicio())
                .fechaFin(request.getFechaFin())
                .ordenVisual(request.getOrdenVisual() != null ? request.getOrdenVisual() : 0)
                .build();

        validatePromotion(promotion);

        Promotion saved = promotionRepository.save(promotion);

        if (Boolean.TRUE.equals(request.getSendNotification())) {
            notificationService.notifyPromotionCreated(saved, true);
        }

        return toOwnerResponse(saved);
    }

    @Override
    public PromotionResponse updatePromotion(Long tenantId, Long promotionId, PromotionRequest request) {
        validatePromotionsFeatureAllowed(tenantId);

        Promotion promotion = promotionRepository.findByIdAndTenant_Id(promotionId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Promoción no encontrada"));

        Branch branch = resolveBranch(tenantId, request.getBranchId());

        promotion.setBranch(branch);
        promotion.setTitulo(trim(request.getTitulo()));
        promotion.setSubtitulo(trim(request.getSubtitulo()));
        promotion.setDescripcion(trim(request.getDescripcion()));
        promotion.setTipo(request.getTipo());
        promotion.setBadge(trim(request.getBadge()));
        promotion.setImageUrl(trim(request.getImageUrl()));
        promotion.setIconName(trim(request.getIconName()));
        promotion.setPriceText(trim(request.getPriceText()));
        promotion.setCtaLabel(trim(request.getCtaLabel()));
        promotion.setRedirectType(request.getRedirectType() != null ? request.getRedirectType() : PromotionRedirectType.NONE);
        promotion.setRedirectValue(trim(request.getRedirectValue()));
        promotion.setDestacado(Boolean.TRUE.equals(request.getDestacado()));
        promotion.setSoloClientesConPuntos(Boolean.TRUE.equals(request.getSoloClientesConPuntos()));
        promotion.setPuntosMinimos(request.getPuntosMinimos());
        promotion.setActivo(request.getActivo() == null || request.getActivo());
        promotion.setFechaInicio(request.getFechaInicio());
        promotion.setFechaFin(request.getFechaFin());
        promotion.setOrdenVisual(request.getOrdenVisual() != null ? request.getOrdenVisual() : 0);

        validatePromotion(promotion);

        return toOwnerResponse(promotionRepository.save(promotion));
    }

    @Override
    public PromotionResponse togglePromotion(Long tenantId, Long promotionId) {
        validatePromotionsFeatureAllowed(tenantId);

        Promotion promotion = promotionRepository.findByIdAndTenant_Id(promotionId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Promoción no encontrada"));

        promotion.setActivo(!promotion.isActivo());

        return toOwnerResponse(promotionRepository.save(promotion));
    }



    @Override
    public PromotionResponse uploadPromotionImage(Long tenantId, Long promotionId, MultipartFile file) {
        validatePromotionsFeatureAllowed(tenantId);

        Promotion promotion = promotionRepository.findByIdAndTenant_Id(promotionId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Promoción no encontrada"));

        CloudinaryStorageService.UploadResult result =
                cloudinaryStorageService.uploadPromotionImage(tenantId, promotionId, file);

        promotion.setImageUrl(result.getSecureUrl());

        return toOwnerResponse(promotionRepository.save(promotion));
    }

    @Override
    public void deletePromotion(Long tenantId, Long promotionId) {
        validatePromotionsFeatureAllowed(tenantId);

        Promotion promotion = promotionRepository.findByIdAndTenant_Id(promotionId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Promoción no encontrada"));

        promotionRepository.delete(promotion);
    }

    private void validatePromotion(Promotion p) {
        if (p.getTitulo() == null || p.getTitulo().isBlank()) {
            throw new IllegalArgumentException("El título es obligatorio");
        }

        if (p.getTipo() == null) {
            throw new IllegalArgumentException("El tipo de promoción es obligatorio");
        }

        if (p.getFechaInicio() != null
                && p.getFechaFin() != null
                && p.getFechaFin().isBefore(p.getFechaInicio())) {
            throw new IllegalArgumentException("La fecha fin no puede ser menor a la fecha inicio");
        }

        if (p.isSoloClientesConPuntos()
                && (p.getPuntosMinimos() == null || p.getPuntosMinimos() < 1)) {
            throw new IllegalArgumentException("Debes indicar puntos mínimos válidos");
        }

        if (!p.isSoloClientesConPuntos()) {
            p.setPuntosMinimos(null);
        }
    }

    private void validatePromotionsFeatureAllowed(Long tenantId) {
        Subscription subscription = subscriptionRepository
                .findTopByTenantIdOrderByFechaInicioDesc(tenantId)
                .orElseThrow(() -> new RuntimeException("Suscripción no encontrada"));

        if (!subscription.isPromotionsEnabled()) {
            throw new RuntimeException("Tu plan actual no permite gestionar promociones");
        }
    }

    private void validatePromotionsCreateAllowed(Long tenantId) {
        Subscription subscription = subscriptionRepository
                .findTopByTenantIdOrderByFechaInicioDesc(tenantId)
                .orElseThrow(() -> new RuntimeException("Suscripción no encontrada"));

        if (!subscription.isPromotionsEnabled()) {
            throw new RuntimeException("Tu plan actual no permite gestionar promociones");
        }

        if ("STARTER".equalsIgnoreCase(subscription.getPlan())) {
            long totalPromotions = promotionRepository.countByTenant_Id(tenantId);
            if (totalPromotions >= 5) {
                throw new RuntimeException("El plan STARTER permite hasta 5 promociones");
            }
        }
    }

    private Branch resolveBranch(Long tenantId, Long branchId) {
        if (branchId == null) {
            return null;
        }

        return branchRepository.findById(branchId)
                .filter(branch -> branch.getTenant() != null && branch.getTenant().getId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Sucursal no válida para este tenant"));
    }

    private ClientPromotionResponse toClientResponse(Promotion p) {
        return new ClientPromotionResponse(
                p.getId(),
                p.getTitulo(),
                p.getSubtitulo(),
                p.getDescripcion(),
                p.getBadge(),
                p.getTipo() != null ? p.getTipo().name() : null,
                p.getIconName(),
                p.getImageUrl(),
                p.getPriceText(),
                p.getCtaLabel(),
                p.getRedirectType() != null ? p.getRedirectType().name() : "NONE",
                p.getRedirectValue(),
                p.isDestacado()
        );
    }

    private PromotionResponse toOwnerResponse(Promotion p) {
        return PromotionResponse.builder()
                .id(p.getId())
                .tenantId(p.getTenant() != null ? p.getTenant().getId() : null)
                .branchId(p.getBranch() != null ? p.getBranch().getId() : null)
                .titulo(p.getTitulo())
                .subtitulo(p.getSubtitulo())
                .descripcion(p.getDescripcion())
                .tipo(p.getTipo() != null ? p.getTipo().name() : null)
                .badge(p.getBadge())
                .imageUrl(p.getImageUrl())
                .iconName(p.getIconName())
                .priceText(p.getPriceText())
                .ctaLabel(p.getCtaLabel())
                .redirectType(p.getRedirectType() != null ? p.getRedirectType().name() : "NONE")
                .redirectValue(p.getRedirectValue())
                .destacado(p.isDestacado())
                .soloClientesConPuntos(p.isSoloClientesConPuntos())
                .puntosMinimos(p.getPuntosMinimos())
                .activo(p.isActivo())
                .fechaInicio(p.getFechaInicio())
                .fechaFin(p.getFechaFin())
                .ordenVisual(p.getOrdenVisual())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
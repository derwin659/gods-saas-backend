package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.RewardItemRequest;
import com.gods.saas.domain.dto.response.RewardItemResponse;
import com.gods.saas.domain.model.RewardItem;
import com.gods.saas.domain.model.Subscription;
import com.gods.saas.domain.model.Tenant;
import com.gods.saas.domain.repository.RewardItemRepository;
import com.gods.saas.domain.repository.SubscriptionRepository;
import com.gods.saas.domain.repository.TenantRepository;
import com.gods.saas.service.impl.impl.RewardItemService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class RewardItemServiceImpl implements RewardItemService {

    private static final int STARTER_MAX_REWARDS = 5;

    private final RewardItemRepository repository;
    private final TenantRepository tenantRepository;
    private final SubscriptionRepository subscriptionRepository;

    @Override
    public List<RewardItemResponse> getAll(Long tenantId, Boolean onlyActive) {
        List<RewardItem> list = Boolean.TRUE.equals(onlyActive)
                ? repository.findByTenant_IdAndActivoTrueOrderByNombreAsc(tenantId)
                : repository.findByTenant_IdOrderByNombreAsc(tenantId);

        return list.stream()
                .map(this::map)
                .toList();
    }

    @Override
    public RewardItemResponse create(Long tenantId, RewardItemRequest request) {
        validateCustomRewardsCreateAllowed(tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant no encontrado"));

        validate(request);

        String nombre = request.getNombre().trim();

        if (repository.existsByTenant_IdAndNombreIgnoreCase(tenantId, nombre)) {
            throw new RuntimeException("Ya existe un premio con ese nombre");
        }

        RewardItem entity = RewardItem.builder()
                .tenant(tenant)
                .nombre(nombre)
                .descripcion(trimToNull(request.getDescripcion()))
                .puntosRequeridos(request.getPuntosRequeridos())
                .stock(request.getStock())
                .imagenUrl(trimToNull(request.getImagenUrl()))
                .activo(request.getActivo() != null ? request.getActivo() : true)
                .build();

        return map(repository.save(entity));
    }

    @Override
    public RewardItemResponse update(Long tenantId, Long id, RewardItemRequest request) {
        validateCustomRewardsFeatureAllowed(tenantId);

        RewardItem entity = repository.findByIdAndTenant_Id(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Premio no encontrado"));

        validate(request);

        String nombre = request.getNombre().trim();

        if (repository.existsByTenant_IdAndNombreIgnoreCaseAndIdNot(tenantId, nombre, id)) {
            throw new RuntimeException("Nombre duplicado");
        }

        entity.setNombre(nombre);
        entity.setDescripcion(trimToNull(request.getDescripcion()));
        entity.setPuntosRequeridos(request.getPuntosRequeridos());
        entity.setStock(request.getStock());
        entity.setImagenUrl(trimToNull(request.getImagenUrl()));
        entity.setActivo(request.getActivo() != null ? request.getActivo() : true);

        return map(repository.save(entity));
    }

    @Override
    public void delete(Long tenantId, Long id) {
        validateCustomRewardsFeatureAllowed(tenantId);

        RewardItem entity = repository.findByIdAndTenant_Id(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Premio no encontrado"));

        repository.delete(entity);
    }

    private void validate(RewardItemRequest request) {
        if (request.getNombre() == null || request.getNombre().trim().isEmpty()) {
            throw new RuntimeException("Nombre obligatorio");
        }

        if (request.getPuntosRequeridos() == null || request.getPuntosRequeridos() <= 0) {
            throw new RuntimeException("Puntos inválidos");
        }

        if (request.getStock() != null && request.getStock() < 0) {
            throw new RuntimeException("Stock inválido");
        }
    }

    private RewardItemResponse map(RewardItem e) {
        return new RewardItemResponse(
                e.getId(),
                e.getNombre(),
                e.getDescripcion(),
                e.getPuntosRequeridos(),
                false
        );
    }

    private void validateCustomRewardsFeatureAllowed(Long tenantId) {
        Subscription subscription = subscriptionRepository
                .findTopByTenantIdOrderByFechaInicioDesc(tenantId)
                .orElseThrow(() -> new RuntimeException("Suscripción no encontrada"));

        if (subscription.getCustomRewardsEnabled() == null || !subscription.getCustomRewardsEnabled()) {
            throw new RuntimeException("Tu plan actual no permite gestionar premios personalizados");
        }
    }

    private void validateCustomRewardsCreateAllowed(Long tenantId) {
        Subscription subscription = subscriptionRepository
                .findTopByTenantIdOrderByFechaInicioDesc(tenantId)
                .orElseThrow(() -> new RuntimeException("Suscripción no encontrada"));

        if (subscription.getCustomRewardsEnabled() == null || !subscription.getCustomRewardsEnabled()) {
            throw new RuntimeException("Tu plan actual no permite gestionar premios personalizados");
        }

        if ("STARTER".equalsIgnoreCase(subscription.getPlan())) {
            long totalRewards = repository.countByTenant_Id(tenantId);

            if (totalRewards >= STARTER_MAX_REWARDS) {
                throw new RuntimeException("El plan STARTER permite hasta 5 premios");
            }
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
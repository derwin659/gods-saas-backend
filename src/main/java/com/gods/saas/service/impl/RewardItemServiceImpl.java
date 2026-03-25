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

    private final RewardItemRepository repository;
    private final TenantRepository tenantRepository;
    private final SubscriptionRepository subscriptionRepository;

    @Override
    public List<RewardItemResponse> getAll(Long tenantId, Boolean onlyActive) {


        List<RewardItem> list = Boolean.TRUE.equals(onlyActive)
                ? repository.findByTenant_IdAndActivoTrueOrderByNombreAsc(tenantId)
                : repository.findByTenant_IdOrderByNombreAsc(tenantId);

        return list.stream().map(this::map).toList();
    }

    @Override
    public RewardItemResponse create(Long tenantId, RewardItemRequest request) {
        validateCustomRewardsAllowed(tenantId);
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant no encontrado"));


        if (repository.existsByTenant_IdAndNombreIgnoreCase(tenantId, request.getNombre())) {
            throw new RuntimeException("Ya existe un premio con ese nombre");
        }

        validate(request);

        RewardItem entity = RewardItem.builder()
                .tenant(tenant)
                .nombre(request.getNombre().trim())
                .descripcion(request.getDescripcion())
                .puntosRequeridos(request.getPuntosRequeridos())
                .stock(request.getStock())
                .imagenUrl(request.getImagenUrl())
                .activo(request.getActivo() != null ? request.getActivo() : true)
                .build();

        return map(repository.save(entity));
    }

    @Override
    public RewardItemResponse update(Long tenantId, Long id, RewardItemRequest request) {

        validateCustomRewardsAllowed(tenantId);
        RewardItem entity = repository.findByIdAndTenant_Id(id, tenantId)
                .orElseThrow(() -> new RuntimeException("No encontrado"));


        if (repository.existsByTenant_IdAndNombreIgnoreCaseAndIdNot(
                tenantId, request.getNombre(), id)) {
            throw new RuntimeException("Nombre duplicado");
        }


        validate(request);

        entity.setNombre(request.getNombre());
        entity.setDescripcion(request.getDescripcion());
        entity.setPuntosRequeridos(request.getPuntosRequeridos());
        entity.setStock(request.getStock());
        entity.setImagenUrl(request.getImagenUrl());
        entity.setActivo(request.getActivo());

        return map(repository.save(entity));
    }

    @Override
    public void delete(Long tenantId, Long id) {
        validateCustomRewardsAllowed(tenantId);
        RewardItem entity = repository.findByIdAndTenant_Id(id, tenantId)
                .orElseThrow(() -> new RuntimeException("No encontrado"));

        repository.delete(entity);
    }

    private void validate(RewardItemRequest request) {
        if (request.getNombre() == null || request.getNombre().isEmpty()) {
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
                e.getNombre(),            // titulo
                e.getDescripcion(),
                e.getPuntosRequeridos(), // costoPuntos
                false                    // destacado (lo puedes agregar luego)
        );
    }


    private void validateCustomRewardsAllowed(Long tenantId) {
        Subscription subscription = subscriptionRepository
                .findTopByTenantIdOrderByFechaInicioDesc(tenantId)
                .orElseThrow(() -> new RuntimeException("Suscripción no encontrada"));

        if (subscription.getCustomRewardsEnabled() == null || !subscription.getCustomRewardsEnabled()) {
            throw new RuntimeException("Tu plan actual no permite crear o editar premios personalizados");
        }
    }
}

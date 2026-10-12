package com.gods.saas.service.impl;

import com.gods.saas.domain.model.ServiceEntity;
import com.gods.saas.domain.repository.ServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.HashSet;

/** Packages are a fixed-price service sold by one professional in one visit. */
@Service
@RequiredArgsConstructor
public class ServicePackageService {
    private final ServiceRepository repository;

    public void configure(Long tenantId, ServiceEntity target, List<Long> ids, Boolean variablePrice) {
        // Older clients omit this field: preserve existing package composition.
        if (ids == null) ids = target.getPackageServiceIds();
        if (ids == null) ids = List.of();
        if (!ids.isEmpty()) {
            if (ids.size() < 2 || ids.size() > 20 || ids.stream().anyMatch(java.util.Objects::isNull)
                    || new HashSet<>(ids).size() != ids.size()) {
                throw new IllegalArgumentException("El paquete debe incluir de 2 a 20 servicios diferentes");
            }
            if (Boolean.TRUE.equals(variablePrice)) {
                throw new IllegalArgumentException("Los paquetes tienen precio fijo");
            }
            requireNotIncluded(tenantId, target.getId());
            validateComponents(tenantId, target.getId(), ids);
        }
        target.setPackageServiceIds(List.copyOf(ids));
    }

    public java.math.BigDecimal salePrice(Long tenantId, ServiceEntity service, Double requestedPrice) {
        if (!Boolean.TRUE.equals(service.getActivo()) || service.getDeletedAt() != null) {
            throw new IllegalArgumentException("Paquete no disponible");
        }
        validateForSale(tenantId, service);
        var price = java.math.BigDecimal.valueOf(service.getPrecio()).setScale(2, java.math.RoundingMode.HALF_UP);
        if (requestedPrice != null && java.math.BigDecimal.valueOf(requestedPrice)
                .setScale(2, java.math.RoundingMode.HALF_UP).compareTo(price) != 0) {
            throw new IllegalArgumentException("El precio del paquete cambió. Actualiza el catálogo y vuelve a seleccionarlo");
        }
        return price;
    }

    public void validateForSale(Long tenantId, ServiceEntity service) {
        if (!service.isServicePackage()) return;
        validateComponents(tenantId, service.getId(), service.getPackageServiceIds());
    }

    private void validateComponents(Long tenantId, Long packageId, List<Long> ids) {
        for (Long id : ids) {
            if (id.equals(packageId)) throw new IllegalArgumentException("Un paquete no puede incluirse a sí mismo");
            ServiceEntity component = repository.findByIdAndTenant_IdAndDeletedAtIsNull(id, tenantId)
                    .filter(s -> Boolean.TRUE.equals(s.getActivo()))
                    .orElseThrow(() -> new IllegalArgumentException("Servicio del paquete no disponible: " + id));
            if (component.isServicePackage()) throw new IllegalArgumentException("No se pueden incluir paquetes dentro de otro paquete");
        }
    }

    public void requireNotIncluded(Long tenantId, Long serviceId) {
        if (serviceId == null) return;
        boolean included = repository.findByTenant_IdAndDeletedAtIsNullOrderByNombreAsc(tenantId).stream()
                .anyMatch(s -> s.isServicePackage() && s.getPackageServiceIds().contains(serviceId));
        if (included) throw new IllegalArgumentException("Este servicio pertenece a un paquete. Retíralo del paquete antes de desactivarlo, eliminarlo o convertirlo en paquete");
    }
}

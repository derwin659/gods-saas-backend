package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.ServiceRequest;
import com.gods.saas.domain.dto.response.ServiceResponse;
import com.gods.saas.domain.model.ServiceEntity;
import com.gods.saas.domain.model.Tenant;
import com.gods.saas.domain.repository.ServiceRepository;
import com.gods.saas.domain.repository.TenantRepository;
import com.gods.saas.service.impl.impl.OwnerServiceCrudService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class OwnerServiceCrudServiceImpl implements OwnerServiceCrudService {

    private final ServiceRepository serviceRepository;
    private final TenantRepository tenantRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ServiceResponse> findAll(Long tenantId, Boolean onlyActive) {
        List<ServiceEntity> services = Boolean.TRUE.equals(onlyActive)
                ? serviceRepository.findByTenant_IdAndActivoTrueOrderByNombreAsc(tenantId)
                : serviceRepository.findByTenant_IdAndActivoTrue(tenantId);

        return services.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ServiceResponse findById(Long tenantId, Long serviceId) {
        ServiceEntity service = getServiceOrThrow(tenantId, serviceId);
        return toResponse(service);
    }

    @Override
    public ServiceResponse create(Long tenantId, ServiceRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant no encontrado"));

        boolean exists = serviceRepository.existsByTenant_IdAndNombreIgnoreCase(
                tenantId, request.nombre().trim()
        );
        if (exists) {
            throw new IllegalArgumentException("Ya existe un servicio con ese nombre");
        }

        ServiceEntity service = new ServiceEntity();
        service.setTenant(tenant);
        service.setNombre(request.nombre().trim());
        service.setDescripcion(trimToNull(request.descripcion()));
        service.setDuracionMinutos(request.duracionMinutos());
        service.setPrecio(request.precio().doubleValue());
        service.setCategoria(trimToNull(request.categoria()));
        service.setActivo(request.activo() != null ? request.activo() : true);

        return toResponse(serviceRepository.save(service));
    }

    @Override
    public ServiceResponse update(Long tenantId, Long serviceId, ServiceRequest request) {
        ServiceEntity service = getServiceOrThrow(tenantId, serviceId);

        boolean exists = serviceRepository.existsByTenant_IdAndNombreIgnoreCaseAndIdNot(
                tenantId, request.nombre().trim(), serviceId
        );
        if (exists) {
            throw new IllegalArgumentException("Ya existe otro servicio con ese nombre");
        }

        service.setNombre(request.nombre().trim());
        service.setDescripcion(trimToNull(request.descripcion()));
        service.setDuracionMinutos(request.duracionMinutos());
        service.setPrecio(request.precio().doubleValue());
        service.setCategoria(trimToNull(request.categoria()));
        service.setActivo(request.activo() != null ? request.activo() : service.getActivo());

        return toResponse(serviceRepository.save(service));
    }

    @Override
    public ServiceResponse toggleStatus(Long tenantId, Long serviceId) {
        ServiceEntity service = getServiceOrThrow(tenantId, serviceId);
        service.setActivo(!Boolean.TRUE.equals(service.getActivo()));
        return toResponse(serviceRepository.save(service));
    }

    private ServiceEntity getServiceOrThrow(Long tenantId, Long serviceId) {
        return serviceRepository.findByIdAndTenant_Id(serviceId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Servicio no encontrado"));
    }

    private ServiceResponse toResponse(ServiceEntity service) {
        return new ServiceResponse(
                service.getId(),
                service.getTenant().getId(),
                service.getNombre(),
                service.getDescripcion(),
                service.getDuracionMinutos(),
                service.getPrecio(),
                service.getCategoria(),
                service.getActivo()
        );
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.ServiceRequest;
import com.gods.saas.domain.dto.request.DeleteServiceRequest;
import com.gods.saas.domain.dto.response.ServiceResponse;
import com.gods.saas.domain.dto.response.ServiceDeletionPreviewResponse;
import com.gods.saas.domain.dto.response.ServiceDeletionResponse;
import com.gods.saas.domain.model.ServiceEntity;
import com.gods.saas.domain.model.Tenant;
import com.gods.saas.domain.repository.ServiceRepository;
import com.gods.saas.domain.repository.BarberBranchServiceRepository;
import com.gods.saas.domain.repository.BarberServiceCommissionRepository;
import com.gods.saas.domain.repository.TenantRepository;
import com.gods.saas.service.impl.impl.OwnerServiceCrudService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Service
@RequiredArgsConstructor
@Transactional
public class OwnerServiceCrudServiceImpl implements OwnerServiceCrudService {

    private final ServiceRepository serviceRepository;
    private final TenantRepository tenantRepository;
    private final CloudinaryStorageService cloudinaryStorageService;
    private final BarberBranchServiceRepository barberBranchServiceRepository;
    private final BarberServiceCommissionRepository barberServiceCommissionRepository;
    private final GeneralAuditService generalAuditService;

    @Override
    @Transactional(readOnly = true)
    public List<ServiceResponse> findAll(Long tenantId, Boolean onlyActive) {
        List<ServiceEntity> services = Boolean.TRUE.equals(onlyActive)
                ? serviceRepository.findByTenant_IdAndActivoTrueAndDeletedAtIsNullOrderByNombreAsc(tenantId)
                : serviceRepository.findByTenant_IdAndDeletedAtIsNullOrderByNombreAsc(tenantId);

        return services.stream()
                .map(this::toResponse)
                .toList();
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

        String nombreLimpio = request.nombre() == null ? null : request.nombre().trim();

        if (nombreLimpio == null || nombreLimpio.isBlank()) {
            throw new IllegalArgumentException("El nombre del servicio es obligatorio");
        }

        boolean exists = serviceRepository.existsByTenant_IdAndNombreIgnoreCaseAndDeletedAtIsNull(
                tenantId,
                nombreLimpio
        );

        if (exists) {
            throw new IllegalArgumentException("Ya existe un servicio con ese nombre en esta barbería");
        }

        ServiceEntity service = new ServiceEntity();
        service.setTenant(tenant);
        service.setNombre(nombreLimpio);
        service.setDescripcion(trimToNull(request.descripcion()));
        service.setDuracionMinutos(request.duracionMinutos());
        service.setPrecio(request.precio().doubleValue());
        service.setPrecioVariable(Boolean.TRUE.equals(request.precioVariable()));
        service.setCategoria(trimToNull(request.categoria()));
        service.setActivo(request.activo() != null ? request.activo() : true);

        return toResponse(serviceRepository.save(service));
    }

    @Override
    public ServiceResponse update(Long tenantId, Long serviceId, ServiceRequest request) {
        ServiceEntity service = getServiceOrThrow(tenantId, serviceId);

        String nombreLimpio = request.nombre() == null ? null : request.nombre().trim();

        if (nombreLimpio == null || nombreLimpio.isBlank()) {
            throw new IllegalArgumentException("El nombre del servicio es obligatorio");
        }

        boolean exists = serviceRepository.existsByTenant_IdAndNombreIgnoreCaseAndIdNotAndDeletedAtIsNull(
                tenantId,
                nombreLimpio,
                serviceId
        );

        if (exists) {
            throw new IllegalArgumentException("Ya existe otro servicio con ese nombre");
        }

        service.setNombre(nombreLimpio);
        service.setDescripcion(trimToNull(request.descripcion()));
        service.setDuracionMinutos(request.duracionMinutos());
        service.setPrecio(request.precio().doubleValue());
        service.setPrecioVariable(Boolean.TRUE.equals(request.precioVariable()));
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

    @Override
    @Transactional(readOnly = true)
    public ServiceDeletionPreviewResponse deletionPreview(Long tenantId, Long serviceId) {
        ServiceEntity service = getServiceOrThrow(tenantId, serviceId);
        long appointments = serviceRepository.countAppointmentReferences(serviceId);
        long saleItems = serviceRepository.countSaleItemReferences(serviceId);
        // sale_detail es una estructura legacy que no existe en todas las bases.
        // El historial vigente y protegido vive en sale_item.
        long legacyDetails = 0;
        long localItems = serviceRepository.countLocalConsumptionReferences(serviceId);
        long promotions = serviceRepository.countPromotionReferences(serviceId);
        long configurations = barberBranchServiceRepository.countByTenant_IdAndService_Id(tenantId, serviceId)
                + barberServiceCommissionRepository.countByTenant_IdAndService_Id(tenantId, serviceId);
        boolean hasHistory = appointments + saleItems + legacyDetails + localItems + promotions + configurations > 0;
        return ServiceDeletionPreviewResponse.builder()
                .serviceId(serviceId).serviceName(service.getNombre())
                .appointments(appointments).saleItems(saleItems)
                .legacySaleDetails(legacyDetails).localConsumptionItems(localItems)
                .promotions(promotions).configurations(configurations)
                .hasHistory(hasHistory)
                .deletionMode(hasHistory ? "ARCHIVE" : "HARD_DELETE")
                .explanation(hasHistory
                        ? "Se eliminará del catálogo y se conservará su historial de ventas y citas."
                        : "Se eliminará definitivamente porque nunca fue utilizado.")
                .build();
    }

    @Override
    public ServiceDeletionResponse delete(Long tenantId, Long serviceId, DeleteServiceRequest request) {
        ServiceEntity service = getServiceOrThrow(tenantId, serviceId);
        ServiceDeletionPreviewResponse preview = deletionPreview(tenantId, serviceId);
        Actor actor = currentActor();
        Map<String, Object> before = Map.of(
                "name", service.getNombre(), "active", Boolean.TRUE.equals(service.getActivo()),
                "appointments", preview.getAppointments(), "saleItems", preview.getSaleItems(),
                "deletionMode", preview.getDeletionMode()
        );
        String oldPublicId = service.getImagePublicId();

        serviceRepository.disablePromotionsForService(serviceId);
        barberServiceCommissionRepository.deleteByTenant_IdAndService_Id(tenantId, serviceId);
        barberBranchServiceRepository.deleteByTenant_IdAndService_Id(tenantId, serviceId);
        barberServiceCommissionRepository.flush();
        barberBranchServiceRepository.flush();

        if (preview.isHasHistory()) {
            service.setActivo(false);
            service.setDeletedAt(LocalDateTime.now());
            service.setDeletedByUserId(actor.userId());
            service.setDeletionReason(request.reason().trim());
            service.setImageUrl(null);
            service.setImagePublicId(null);
            serviceRepository.save(service);
        } else {
            serviceRepository.delete(service);
            serviceRepository.flush();
        }

        if (oldPublicId != null && !oldPublicId.isBlank()) {
            cloudinaryStorageService.deleteImage(oldPublicId);
        }

        generalAuditService.record(tenantId, null, actor.userId(), actor.role(),
                "SERVICE", serviceId, preview.isHasHistory() ? "ARCHIVE" : "DELETE",
                request.reason(), before,
                Map.of("deleted", true, "historyPreserved", preview.isHasHistory()));

        return ServiceDeletionResponse.builder()
                .serviceId(serviceId).serviceName(service.getNombre())
                .deletionMode(preview.getDeletionMode()).deleted(true)
                .historyPreserved(preview.isHasHistory())
                .message(preview.isHasHistory()
                        ? "Servicio eliminado del catálogo. Su historial fue protegido."
                        : "Servicio eliminado definitivamente.")
                .build();
    }

    private Actor currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getDetails() instanceof Map<?, ?> details) {
            Object rawUserId = details.get("userId");
            Object rawRole = details.get("role");
            Long userId = rawUserId instanceof Number value ? value.longValue() : null;
            return new Actor(userId, rawRole == null ? null : rawRole.toString());
        }
        return new Actor(null, null);
    }

    private record Actor(Long userId, String role) {}

    @Override
    public ServiceResponse uploadImage(Long tenantId, Long serviceId, MultipartFile file) {
        ServiceEntity service = getServiceOrThrow(tenantId, serviceId);

        String oldPublicId = service.getImagePublicId();

        CloudinaryStorageService.UploadResult result =
                cloudinaryStorageService.uploadServiceImage(tenantId, serviceId, file);

        service.setImageUrl(result.getSecureUrl());
        service.setImagePublicId(result.getPublicId());

        ServiceEntity saved = serviceRepository.save(service);

        if (oldPublicId != null && !oldPublicId.isBlank()) {
            cloudinaryStorageService.deleteImage(oldPublicId);
        }

        return toResponse(saved);
    }

    @Override
    public ServiceResponse deleteImage(Long tenantId, Long serviceId) {
        ServiceEntity service = getServiceOrThrow(tenantId, serviceId);

        String oldPublicId = service.getImagePublicId();

        service.setImageUrl(null);
        service.setImagePublicId(null);

        ServiceEntity saved = serviceRepository.save(service);

        if (oldPublicId != null && !oldPublicId.isBlank()) {
            cloudinaryStorageService.deleteImage(oldPublicId);
        }

        return toResponse(saved);
    }

    private ServiceEntity getServiceOrThrow(Long tenantId, Long serviceId) {
        return serviceRepository.findByIdAndTenant_IdAndDeletedAtIsNull(serviceId, tenantId)
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
                Boolean.TRUE.equals(service.getPrecioVariable()),
                service.getCategoria(),
                service.getActivo(),
                service.getImageUrl()
        );
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

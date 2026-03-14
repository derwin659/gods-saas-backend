package com.gods.saas.service.impl;


import com.gods.saas.domain.dto.request.QuickRegisterCustomerRequest;
import com.gods.saas.domain.dto.response.BarberServiceResponse;
import com.gods.saas.domain.dto.response.CustomerLookupResponse;
import com.gods.saas.domain.model.Customer;
import com.gods.saas.domain.model.ServiceEntity;
import com.gods.saas.domain.model.Tenant;
import com.gods.saas.domain.repository.CustomerRepository;
import com.gods.saas.domain.repository.ServiceRepository;
import com.gods.saas.domain.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BarberAttendService {

    private final CustomerRepository customerRepository;
    private final TenantRepository tenantRepository;

    private final ServiceRepository serviceRepository;


    @Transactional(readOnly = true)
    public CustomerLookupResponse findCustomerByPhone(Long tenantId, String phone) {
        String normalizedPhone = normalizePhone(phone);

        return customerRepository.findByTenant_IdAndTelefono(tenantId, normalizedPhone)
                .map(c -> CustomerLookupResponse.builder()
                        .found(true)
                        .id(c.getId())
                        .nombre(c.getNombres())
                        .apellido(c.getApellidos())
                        .phone(c.getTelefono())
                        .tenantId(c.getTenant().getId())
                        .puntosDisponibles(c.getPuntosDisponibles() != null ? c.getPuntosDisponibles() : 0)
                        .mensaje("Cliente encontrado")
                        .build())
                .orElse(CustomerLookupResponse.builder()
                        .found(false)
                        .phone(normalizedPhone)
                        .tenantId(tenantId)
                        .puntosDisponibles(0)
                        .mensaje("Cliente no encontrado")
                        .build());
    }

    @Transactional
    public List<BarberServiceResponse> listServices(Long tenantId) {
        return serviceRepository.findByTenant_IdAndActivoTrueOrderByNombreAsc(tenantId)
                .stream()
                .map(service -> BarberServiceResponse.builder()
                        .id(service.getId())
                        .nombre(service.getNombre())
                        .descripcion(service.getDescripcion())
                        .duracionMin(service.getDuracionMinutos())
                        .precio(service.getPrecio())
                        .categoria(service.getCategoria())
                        .activo(Boolean.TRUE.equals(service.getActivo()))
                        .build())
                .toList();
    }

    @Transactional
    public CustomerLookupResponse quickRegister(QuickRegisterCustomerRequest request) {
        String normalizedPhone = normalizePhone(request.getTelefono());

        if (customerRepository.existsByTenant_IdAndTelefono(request.getTenantId(), normalizedPhone)) {
            Customer existing = customerRepository
                    .findByTenant_IdAndTelefono(request.getTenantId(), normalizedPhone)
                    .orElseThrow();

            return CustomerLookupResponse.builder()
                    .found(true)
                    .id(existing.getId())
                    .nombre(existing.getNombres())
                    .apellido(existing.getApellidos())
                    .phone(existing.getTelefono())
                    .tenantId(existing.getTenant().getId())
                    .puntosDisponibles(existing.getPuntosDisponibles() != null ? existing.getPuntosDisponibles() : 0)
                    .mensaje("El cliente ya existía")
                    .build();
        }

        Tenant tenant = tenantRepository.findById(request.getTenantId())
                .orElseThrow(() -> new RuntimeException("Tenant no encontrado"));

        Customer customer = new Customer();
        customer.setTenant(tenant);
        customer.setTelefono(normalizedPhone);
        customer.setNombres(clean(request.getNombres()));
        customer.setApellidos(clean(request.getApellidos()));
        customer.setOrigenCliente(clean(request.getOrigenCliente()));
        customer.setPhoneVerified(false);
        customer.setPuntosDisponibles(0);

        Customer saved = customerRepository.save(customer);

        return CustomerLookupResponse.builder()
                .found(true)
                .id(saved.getId())
                .nombre(saved.getNombres())
                .apellido(saved.getApellidos())
                .phone(saved.getTelefono())
                .tenantId(saved.getTenant().getId())
                .puntosDisponibles(saved.getPuntosDisponibles() != null ? saved.getPuntosDisponibles() : 0)
                .mensaje("Cliente registrado correctamente")
                .build();
    }

    private String normalizePhone(String phone) {
        if (phone == null) return "";
        return phone.replaceAll("[^0-9]", "").trim();
    }

    private String clean(String value) {
        return value == null ? null : value.trim();
    }
}

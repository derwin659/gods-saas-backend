package com.gods.saas.service.impl;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import com.gods.saas.domain.dto.response.SimpleBarberResponse;
import com.gods.saas.domain.dto.response.SimpleCustomerResponse;
import com.gods.saas.domain.dto.response.SimpleServiceResponse;
import com.gods.saas.domain.model.AppUser;
import com.gods.saas.domain.model.Customer;
import com.gods.saas.domain.model.ServiceEntity;
import com.gods.saas.domain.repository.AppUserRepository;
import com.gods.saas.domain.repository.CustomerRepository;
import com.gods.saas.domain.repository.ServiceRepository;
import com.gods.saas.service.impl.impl.OwnerCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OwnerCatalogServiceImpl implements OwnerCatalogService {

    private final AppUserRepository appUserRepository;
    private final ServiceRepository serviceRepository;
    private final CustomerRepository customerRepository;

    @Override
    public List<SimpleBarberResponse> getBarbers(Long tenantId) {
        return appUserRepository
                .findByTenant_IdAndRolAndActivoTrueOrderByNombreAsc(tenantId, "BARBER")
                .stream()
                .map(this::mapBarber)
                .toList();
    }


    @Override
    public List<SimpleServiceResponse> getServices(Long tenantId) {
        return serviceRepository
                .findByTenant_IdAndActivoTrueOrderByNombreAsc(tenantId)
                .stream()
                .map(this::mapService)
                .toList();
    }

    @Override
    public List<SimpleCustomerResponse> searchCustomers(Long tenantId, String query) {
        final String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            return List.of();
        }

        Pageable pageable = (Pageable) PageRequest.of(0, 10);

        List<Customer> byName = customerRepository
                .findByTenant_IdAndNombresContainingIgnoreCaseOrderByNombresAsc(tenantId, q, pageable);

        List<Customer> byPhone = customerRepository
                .findByTenant_IdAndTelefonoContainingOrderByNombresAsc(tenantId, q, pageable);

        Map<Long, Customer> unique = new LinkedHashMap<>();
        for (Customer c : byName) unique.put(c.getId(), c);
        for (Customer c : byPhone) unique.put(c.getId(), c);

        List<SimpleCustomerResponse> result = new ArrayList<>();
        for (Customer c : unique.values()) {
            result.add(mapCustomer(c));
        }
        return result;
    }

    private SimpleBarberResponse mapBarber(AppUser user) {
        return SimpleBarberResponse.builder()
                .id(user.getId())
                .nombre(user.getNombre())
                .email(user.getEmail())
                .build();
    }

    private SimpleServiceResponse mapService(ServiceEntity service) {
        BigDecimal precio = service.getPrecio() == null
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(service.getPrecio().doubleValue());

        return SimpleServiceResponse.builder()
                .id(service.getId())
                .nombre(service.getNombre())
                .precio(precio)
                .activo(service.getActivo())
                .build();
    }

    private SimpleCustomerResponse mapCustomer(Customer customer) {
        return SimpleCustomerResponse.builder()
                .id(customer.getId())
                .nombres(customer.getNombres())
                .telefono(customer.getTelefono())
                .build();
    }
}
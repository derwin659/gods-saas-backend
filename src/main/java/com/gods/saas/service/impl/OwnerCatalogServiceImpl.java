package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.response.SimpleBarberResponse;
import com.gods.saas.domain.dto.response.SimpleCustomerResponse;
import com.gods.saas.domain.dto.response.SimpleServiceResponse;
import com.gods.saas.domain.model.AppUser;
import com.gods.saas.domain.model.Customer;
import com.gods.saas.domain.model.RoleType;
import com.gods.saas.domain.model.ServiceEntity;
import com.gods.saas.domain.repository.CustomerRepository;
import com.gods.saas.domain.repository.ServiceRepository;
import com.gods.saas.domain.repository.UserTenantRoleRepository;
import com.gods.saas.service.impl.impl.OwnerCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OwnerCatalogServiceImpl implements OwnerCatalogService {

    private final UserTenantRoleRepository userTenantRoleRepository;
    private final ServiceRepository serviceRepository;
    private final CustomerRepository customerRepository;

    @Override
    public List<SimpleBarberResponse> getBarbers(Long tenantId, Long branchId) {
        return userTenantRoleRepository
                .findActiveUsersByTenantBranchAndRole(tenantId, branchId, RoleType.BARBER)
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

        Pageable pageable = PageRequest.of(0, 10);

        return customerRepository
                .searchByNameOrPhone(tenantId, q, pageable)
                .stream()
                .map(this::mapCustomer)
                .toList();
    }
    private SimpleBarberResponse mapBarber(AppUser user) {
        return SimpleBarberResponse.builder()
                .id(user.getId())
                .nombre(user.getNombre())
                .email(user.getEmail())
                .photoUrl(user.getPhotoUrl())
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
                .duracionMinutos(service.getDuracionMinutos())
                .precioVariable(Boolean.TRUE.equals(service.getPrecioVariable()))
                .activo(service.getActivo())
                .imageUrl(service.getImageUrl())
                .build();
    }

    private SimpleCustomerResponse mapCustomer(Customer customer) {
        String nombres = customer.getNombres() == null ? "" : customer.getNombres().trim();
        String apellidos = customer.getApellidos() == null ? "" : customer.getApellidos().trim();
        String nombreCompleto = (nombres + " " + apellidos).trim();
        return SimpleCustomerResponse.builder()
                .id(customer.getId())
                .nombres(nombres)
                .apellidos(apellidos)
                .nombreCompleto(nombreCompleto.isBlank() ? "Cliente" : nombreCompleto)
                .telefono(customer.getTelefono())
                .build();
    }
}

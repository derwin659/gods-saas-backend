package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.CreateQuickCustomerRequest;
import com.gods.saas.domain.dto.response.CustomerSearchResponse;
import com.gods.saas.domain.model.Customer;
import com.gods.saas.domain.model.LoyaltyAccount;
import com.gods.saas.domain.model.Tenant;
import com.gods.saas.domain.repository.CustomerRepository;
import com.gods.saas.domain.repository.LoyaltyAccountRepository;
import com.gods.saas.domain.repository.TenantRepository;
import com.gods.saas.service.impl.impl.CustomerSearchService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomerSearchServiceImpl implements CustomerSearchService {

    private final CustomerRepository customerRepository;
    private final TenantRepository tenantRepository;
    private final LoyaltyAccountRepository loyaltyAccountRepository;
    private final InternationalPhoneService internationalPhoneService;

    @Override
    @Transactional(readOnly = true)
    public List<CustomerSearchResponse> search(Long tenantId, String q) {

        String query = q == null ? "" : q.trim();

        if (query.length() < 2) {
            return List.of();
        }

        String digits = query.replaceAll("[^0-9]", "");
        boolean phoneQuery = digits.length() >= 6
                && !query.matches(".*[A-Za-z].*");
        if (phoneQuery) {
            Tenant tenant = tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new EntityNotFoundException("Tenant no encontrado"));
            try {
                InternationalPhoneService.NormalizedPhone normalized =
                        internationalPhoneService.normalize(tenant, query);
                Optional<Customer> phoneMatch = findPhoneCandidate(tenantId, normalized);
                if (phoneMatch.isPresent()) {
                    return List.of(map(phoneMatch.get()));
                }
            } catch (ResponseStatusException ignored) {
                // Conserva la busqueda parcial para telefonos antiguos o incompletos.
            }
        }

        return customerRepository
                .searchByNameOrPhone(tenantId, query, PageRequest.of(0, 10))
                .stream()
                .map(this::map)
                .toList();
    }

    @Override
    @Transactional
    public CustomerSearchResponse createQuick(Long tenantId, CreateQuickCustomerRequest request) {

        String nombres = request.getNombres() == null ? "" : request.getNombres().trim();
        String apellidos = request.getApellidos() == null ? "" : request.getApellidos().trim();
        String telefono = request.getTelefono() == null ? "" : request.getTelefono().trim();

        if (nombres.isBlank()) {
            throw new IllegalArgumentException("El nombre es obligatorio.");
        }

        if (telefono.isBlank()) {
            throw new IllegalArgumentException("El teléfono es obligatorio.");
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant no encontrado"));

        InternationalPhoneService.NormalizedPhone normalizedPhone =
                internationalPhoneService.normalize(tenant, telefono);
        if (findPhoneCandidate(tenantId, normalizedPhone).isPresent()) {
            throw new IllegalArgumentException("Ya existe un cliente con ese telefono.");
        }

        Customer customer = new Customer();
        customer.setTenant(tenant);
        customer.setNombres(nombres);
        customer.setApellidos(apellidos);
        customer.setTelefono(normalizedPhone.e164());

        // valores por defecto
        customer.setPhoneVerified(false);
        customer.setPhonePendiente(null);
        customer.setPhonePendienteVerificacion(false);
        customer.setOrigenCliente("CAJA");
        customer.setPuntosDisponibles(0);
        customer.setFechaRegistro(LocalDateTime.now());
        customer.setFechaActualizacion(LocalDateTime.now());
        customer.setMigrated(false);
        customer.setAppActivated(false);
        customer.setWelcomeBonusGranted(false);
        customer.setActivationBonusGranted(false);
        customer.setSource("POS");

        customerRepository.save(customer);

        return map(customer);
    }

    private Optional<Customer> findPhoneCandidate(
            Long tenantId,
            InternationalPhoneService.NormalizedPhone phone
    ) {
        String thirdLookup = phone.lookupDigits().stream()
                .filter(value -> !value.equals(phone.internationalDigits()))
                .filter(value -> !value.equals(phone.nationalDigits()))
                .findFirst()
                .orElse(phone.nationalDigits());

        return customerRepository.findPhoneCandidates(
                        tenantId,
                        phone.e164(),
                        phone.internationalDigits(),
                        phone.nationalDigits(),
                        thirdLookup
                )
                .stream()
                .findFirst();
    }

    private CustomerSearchResponse map(Customer c) {
        String fullName = ((c.getNombres() == null ? "" : c.getNombres()) + " " +
                (c.getApellidos() == null ? "" : c.getApellidos())).trim();

        Integer puntosDisponibles = loyaltyAccountRepository
                .findByTenant_IdAndCustomer_Id(c.getTenant().getId(), c.getId())
                .map(LoyaltyAccount::getPuntosDisponibles)
                .orElse(0);

        if (puntosDisponibles == null) {
            puntosDisponibles = 0;
        }

        return CustomerSearchResponse.builder()
                .customerId(c.getId())
                .nombreCompleto(fullName.isBlank() ? "Cliente" : fullName)
                .telefono(c.getTelefono())
                .puntosDisponibles(puntosDisponibles)
                .build();
    }
}
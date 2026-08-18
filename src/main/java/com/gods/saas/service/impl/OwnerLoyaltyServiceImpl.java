package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.ManualPointsAdjustmentRequest;
import com.gods.saas.domain.dto.response.ManualPointsAdjustmentResponse;
import com.gods.saas.domain.dto.response.OwnerCustomerLoyaltyResponse;
import com.gods.saas.domain.dto.response.LoyaltyTierConfig;
import com.gods.saas.domain.model.Customer;
import com.gods.saas.domain.model.LoyaltyAccount;
import com.gods.saas.domain.model.LoyaltyMovement;
import com.gods.saas.domain.model.Tenant;
import com.gods.saas.domain.repository.CustomerRepository;
import com.gods.saas.domain.repository.LoyaltyAccountRepository;
import com.gods.saas.domain.repository.LoyaltyMovementRepository;
import com.gods.saas.domain.repository.TenantRepository;
import com.gods.saas.service.impl.impl.OwnerLoyaltyService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OwnerLoyaltyServiceImpl implements OwnerLoyaltyService {

    private final CustomerRepository customerRepository;
    private final LoyaltyAccountRepository loyaltyAccountRepository;
    private final LoyaltyMovementRepository loyaltyMovementRepository;
    private final OwnerLoyaltySettingsService ownerLoyaltySettingsService;
    private final TenantRepository tenantRepository;
    private final InternationalPhoneService internationalPhoneService;

    @Override
    public OwnerCustomerLoyaltyResponse findCustomerByPhone(Long tenantId, String phone) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Negocio no encontrado."));
        InternationalPhoneService.NormalizedPhone normalized = internationalPhoneService.normalize(tenant, phone);
        String legacyNational = normalized.belongsToTenantRegion()
                ? normalized.nationalDigits()
                : normalized.internationalDigits();
        String legacyFormatted = normalized.belongsToTenantRegion()
                ? normalized.lookupDigits().stream().skip(2).findFirst().orElse(legacyNational)
                : normalized.internationalDigits();
        List<Customer> candidates = customerRepository.findPhoneCandidates(
                tenantId, normalized.e164(), normalized.internationalDigits(), legacyNational, legacyFormatted);
        if (candidates.isEmpty()) {
            throw new EntityNotFoundException("Cliente no encontrado para ese telÃ©fono.");
        }
        if (candidates.size() > 1) {
            throw new IllegalStateException("Hay mÃ¡s de un cliente con este telÃ©fono. Revisa los duplicados antes de ajustar puntos.");
        }
        Customer customer = candidates.get(0);
        if (!normalized.e164().equals(customer.getTelefono())) {
            customer.setTelefono(normalized.e164());
            customer.setFechaActualizacion(LocalDateTime.now());
            customer = customerRepository.save(customer);
        }

        Customer resolvedCustomer = customer;

        LoyaltyAccount loyaltyAccount = loyaltyAccountRepository
                .findByTenant_IdAndCustomer_Id(tenantId, customer.getId())
                .orElseGet(() -> createEmptyAccount(resolvedCustomer));

        int puntosDisponibles = safeInt(loyaltyAccount.getPuntosDisponibles());
        int puntosAcumulados = safeInt(loyaltyAccount.getPuntosAcumulados());
        LoyaltyTierConfig tier = ownerLoyaltySettingsService.resolveTier(tenantId, puntosAcumulados);

        return new OwnerCustomerLoyaltyResponse(
                customer.getId(),
                customer.getNombres(),
                customer.getApellidos(),
                customer.getTelefono(),
                puntosDisponibles,
                puntosAcumulados,
                customer.getMigrated(),
                customer.getAppActivated(),
                "NEW",
                tier != null ? tier.getName() : null,
                tier != null ? tier.getColorHex() : null,
                0L,
                0L,
                null
        );
    }

    @Override
    @Transactional
    public ManualPointsAdjustmentResponse adjustPointsManually(
            Long tenantId,
            Long performedByUserId,
            ManualPointsAdjustmentRequest request
    ) {
        if (request.pointsDelta() == null || request.pointsDelta() == 0) {
            throw new IllegalArgumentException("El ajuste de puntos no puede ser 0.");
        }

        if (request.reason() == null || request.reason().trim().isEmpty()) {
            throw new IllegalArgumentException("El motivo es obligatorio.");
        }

        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new EntityNotFoundException("Cliente no encontrado."));

        if (customer.getTenant() == null || !tenantId.equals(customer.getTenant().getId())) {
            throw new IllegalArgumentException("El cliente no pertenece al tenant actual.");
        }

        Customer resolvedCustomer = customer;

        LoyaltyAccount loyaltyAccount = loyaltyAccountRepository
                .findByTenant_IdAndCustomer_Id(tenantId, customer.getId())
                .orElseGet(() -> createEmptyAccount(resolvedCustomer));

        int previousPoints = safeInt(loyaltyAccount.getPuntosDisponibles());
        int newPoints = previousPoints + request.pointsDelta();

        if (newPoints < 0) {
            throw new IllegalArgumentException("El ajuste dejaría el saldo en negativo.");
        }

        loyaltyAccount.setPuntosDisponibles(newPoints);

        int puntosAcumulados = safeInt(loyaltyAccount.getPuntosAcumulados());
        LoyaltyTierConfig tier = ownerLoyaltySettingsService.resolveTier(tenantId, puntosAcumulados);

        if (request.pointsDelta() > 0) {
            loyaltyAccount.setPuntosAcumulados(puntosAcumulados + request.pointsDelta());
        }

        loyaltyAccount.setFechaUltimoMovimiento(LocalDateTime.now());
        loyaltyAccountRepository.save(loyaltyAccount);

        customer.setPuntosDisponibles(newPoints);
        customer.setFechaActualizacion(LocalDateTime.now());
        customerRepository.save(customer);

        LoyaltyMovement movement = new LoyaltyMovement();
        movement.setTenantId(customer.getTenant().getId());
        movement.setCustomerId(customer.getId());
        movement.setLoyaltyId(loyaltyAccount.getId());
        movement.setTipo("ADJUST");
        movement.setOrigen("MANUAL");
        movement.setReferenciaId(null);
        movement.setDescripcion(request.reason().trim());
        movement.setPuntos(request.pointsDelta());
        movement.setSaldoResultante(newPoints);
        movement.setFechaCreacion(LocalDateTime.now(ZoneOffset.UTC));

        if (performedByUserId != null) {
            movement.setCreadoPor(performedByUserId);
        }

        loyaltyMovementRepository.save(movement);

        return new ManualPointsAdjustmentResponse(
                customer.getId(),
                previousPoints,
                newPoints,
                request.pointsDelta(),
                request.reason().trim(),
                "Ajuste realizado correctamente."
        );
    }

    private LoyaltyAccount createEmptyAccount(Customer customer) {
        LoyaltyAccount account = new LoyaltyAccount();
        account.setTenant(customer.getTenant());
        account.setCustomer(customer);
        account.setPuntosAcumulados(0);
        account.setPuntosDisponibles(0);
        account.setFechaUltimoMovimiento(LocalDateTime.now());
        return loyaltyAccountRepository.save(account);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

}

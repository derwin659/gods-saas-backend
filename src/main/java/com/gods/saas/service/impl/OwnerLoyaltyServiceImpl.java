package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.ManualPointsAdjustmentRequest;
import com.gods.saas.domain.dto.response.ManualPointsAdjustmentResponse;
import com.gods.saas.domain.dto.response.OwnerCustomerLoyaltyResponse;
import com.gods.saas.domain.model.Customer;
import com.gods.saas.domain.model.LoyaltyAccount;
import com.gods.saas.domain.model.LoyaltyMovement;
import com.gods.saas.domain.repository.CustomerRepository;
import com.gods.saas.domain.repository.LoyaltyAccountRepository;
import com.gods.saas.domain.repository.LoyaltyMovementRepository;
import com.gods.saas.service.impl.impl.OwnerLoyaltyService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OwnerLoyaltyServiceImpl implements OwnerLoyaltyService {

    private final CustomerRepository customerRepository;
    private final LoyaltyAccountRepository loyaltyAccountRepository;
    private final LoyaltyMovementRepository loyaltyMovementRepository;

    @Override
    public OwnerCustomerLoyaltyResponse findCustomerByPhone(Long tenantId, String phone) {
        final String normalizedPhone = normalizePhone(phone);

        Customer customer = customerRepository
                .findByTenant_IdAndTelefono(tenantId, normalizedPhone)
                .orElseThrow(() -> new EntityNotFoundException("Cliente no encontrado para ese teléfono."));

        LoyaltyAccount loyaltyAccount = loyaltyAccountRepository
                .findByTenant_IdAndCustomer_Id(tenantId, customer.getId())
                .orElseGet(() -> createEmptyAccount(customer));

        int puntosDisponibles = safeInt(loyaltyAccount.getPuntosDisponibles());

        return new OwnerCustomerLoyaltyResponse(
                customer.getId(),
                customer.getNombres(),
                customer.getApellidos(),
                customer.getTelefono(),
                puntosDisponibles,
                customer.getMigrated(),
                customer.getAppActivated()
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

        LoyaltyAccount loyaltyAccount = loyaltyAccountRepository
                .findByTenant_IdAndCustomer_Id(tenantId, customer.getId())
                .orElseGet(() -> createEmptyAccount(customer));

        int previousPoints = safeInt(loyaltyAccount.getPuntosDisponibles());
        int newPoints = previousPoints + request.pointsDelta();

        if (newPoints < 0) {
            throw new IllegalArgumentException("El ajuste dejaría el saldo en negativo.");
        }

        loyaltyAccount.setPuntosDisponibles(newPoints);

        Integer acumulados = loyaltyAccount.getPuntosAcumulados();
        if (acumulados == null) {
            acumulados = 0;
        }

        if (request.pointsDelta() > 0) {
            loyaltyAccount.setPuntosAcumulados(acumulados + request.pointsDelta());
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
        movement.setFechaCreacion(LocalDateTime.now());

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

    private String normalizePhone(String phone) {
        return phone == null ? "" : phone.trim();
    }
}

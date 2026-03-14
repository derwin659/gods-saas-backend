package com.gods.saas.service.impl;

import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.CustomerRepository;
import com.gods.saas.domain.repository.LoyaltyAccountRepository;
import com.gods.saas.domain.repository.LoyaltyMovementRepository;
import com.gods.saas.domain.repository.LoyaltyPointLotRepository;
import com.gods.saas.service.impl.impl.LoyaltyService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class LoyaltyServiceImpl implements LoyaltyService {

    private static final int POINTS_PER_SOL = 5;
    private static final int BONUS_NEW_CUSTOMER = 100;
    private static final int BONUS_MIGRATED_APP_ACTIVATION = 50;
    private static final int POINTS_EXPIRATION_DAYS = 180;

    private final LoyaltyAccountRepository loyaltyAccountRepository;
    private final LoyaltyMovementRepository loyaltyMovementRepository;
    private final LoyaltyPointLotRepository loyaltyPointLotRepository;
    private final CustomerRepository customerRepository;

    @Override
    public void grantSalePoints(Tenant tenant, Customer customer, AppUser user, Sale sale, double total) {
        if (customer == null || total <= 0) {
            return;
        }

        LoyaltyAccount loyalty = getOrCreateAccount(tenant, customer);

        int puntosGanados = calcularPuntos(total);
        if (puntosGanados <= 0) {
            return;
        }

        int acumulados = safeInt(loyalty.getPuntosAcumulados()) + puntosGanados;
        int disponibles = safeInt(loyalty.getPuntosDisponibles()) + puntosGanados;

        loyalty.setPuntosAcumulados(acumulados);
        loyalty.setPuntosDisponibles(disponibles);
        loyalty.setFechaUltimoMovimiento(LocalDateTime.now());
        loyaltyAccountRepository.save(loyalty);

        LoyaltyMovement mov = new LoyaltyMovement();
        mov.setTenantId(tenant.getId());
        mov.setCustomerId(customer.getId());
        mov.setLoyaltyId(loyalty.getId());
        mov.setTipo("EARN");
        mov.setOrigen("SALE");
        mov.setReferenciaId(sale.getId());
        mov.setDescripcion("Puntos generados por venta #" + sale.getId());
        mov.setPuntos(puntosGanados);
        mov.setSaldoResultante(disponibles);
        mov.setCreadoPor(user != null ? user.getId() : null);
        mov.setFechaCreacion(LocalDateTime.now());
        loyaltyMovementRepository.save(mov);

        LoyaltyPointLot lot = LoyaltyPointLot.builder()
                .tenantId(tenant.getId())
                .customerId(customer.getId())
                .loyaltyAccount(loyalty)
                .movement(mov)
                .sourceType("SALE")
                .sourceReferenceId(sale.getId())
                .pointsEarned(puntosGanados)
                .pointsAvailable(puntosGanados)
                .earnedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(POINTS_EXPIRATION_DAYS))
                .status("ACTIVE")
                .build();

        loyaltyPointLotRepository.save(lot);
    }

    @Override
    public void grantActivationBonusIfNeeded(Customer customer) {
        if (customer == null) return;

        boolean migrated = Boolean.TRUE.equals(customer.getMigrated());
        boolean alreadyGranted = Boolean.TRUE.equals(customer.getActivationBonusGranted());

        if (!migrated || alreadyGranted) {
            markAppActivated(customer);
            return;
        }

        LoyaltyAccount loyalty = getOrCreateAccount(customer.getTenant(), customer);

        int disponibles = safeInt(loyalty.getPuntosDisponibles()) + BONUS_MIGRATED_APP_ACTIVATION;
        int acumulados = safeInt(loyalty.getPuntosAcumulados()) + BONUS_MIGRATED_APP_ACTIVATION;

        loyalty.setPuntosDisponibles(disponibles);
        loyalty.setPuntosAcumulados(acumulados);
        loyalty.setFechaUltimoMovimiento(LocalDateTime.now());
        loyaltyAccountRepository.save(loyalty);

        LoyaltyMovement mov = new LoyaltyMovement();
        mov.setTenantId(customer.getTenant().getId());
        mov.setCustomerId(customer.getId());
        mov.setLoyaltyId(loyalty.getId());
        mov.setTipo("EARN");
        mov.setOrigen("APP_ACTIVATION");
        mov.setReferenciaId(customer.getId());
        mov.setDescripcion("Bono por activar la app");
        mov.setPuntos(BONUS_MIGRATED_APP_ACTIVATION);
        mov.setSaldoResultante(disponibles);
        mov.setCreadoPor(null);
        mov.setFechaCreacion(LocalDateTime.now());
        loyaltyMovementRepository.save(mov);

        customer.setAppActivated(true);
        customer.setAppActivatedAt(LocalDateTime.now());
        customer.setActivationBonusGranted(true);
        customer.setFechaActualizacion(LocalDateTime.now());
        customerRepository.save(customer);
    }

    @Override
    public void grantWelcomeBonusIfNeeded(Customer customer) {
        if (customer == null) return;

        boolean migrated = Boolean.TRUE.equals(customer.getMigrated());
        boolean alreadyGranted = Boolean.TRUE.equals(customer.getWelcomeBonusGranted());

        if (migrated || alreadyGranted) {
            return;
        }

        LoyaltyAccount loyalty = getOrCreateAccount(customer.getTenant(), customer);

        int disponibles = safeInt(loyalty.getPuntosDisponibles()) + BONUS_NEW_CUSTOMER;
        int acumulados = safeInt(loyalty.getPuntosAcumulados()) + BONUS_NEW_CUSTOMER;

        loyalty.setPuntosDisponibles(disponibles);
        loyalty.setPuntosAcumulados(acumulados);
        loyalty.setFechaUltimoMovimiento(LocalDateTime.now());
        loyaltyAccountRepository.save(loyalty);

        LoyaltyMovement mov = new LoyaltyMovement();
        mov.setTenantId(customer.getTenant().getId());
        mov.setCustomerId(customer.getId());
        mov.setLoyaltyId(loyalty.getId());
        mov.setTipo("EARN");
        mov.setOrigen("WELCOME_BONUS");
        mov.setReferenciaId(customer.getId());
        mov.setDescripcion("Bono de bienvenida");
        mov.setPuntos(BONUS_NEW_CUSTOMER);
        mov.setSaldoResultante(disponibles);
        mov.setCreadoPor(null);
        mov.setFechaCreacion(LocalDateTime.now());
        loyaltyMovementRepository.save(mov);

        customer.setWelcomeBonusGranted(true);
        customer.setFechaActualizacion(LocalDateTime.now());
        customerRepository.save(customer);
    }
    @Override
    public int expirePoints() {
        List<LoyaltyPointLot> expiredLots = loyaltyPointLotRepository
                .findByStatusAndExpiresAtBefore("ACTIVE", LocalDateTime.now());

        int processed = 0;

        for (LoyaltyPointLot lot : expiredLots) {
            int pointsToExpire = safeInt(lot.getPointsAvailable());
            if (pointsToExpire <= 0) {
                lot.setStatus("EXPIRED");
                loyaltyPointLotRepository.save(lot);
                continue;
            }

            Optional<LoyaltyAccount> loyaltyOpt = loyaltyAccountRepository
                    .findByTenant_IdAndCustomer_Id(lot.getTenantId(), lot.getCustomerId());

            if (loyaltyOpt.isEmpty()) {
                lot.setStatus("EXPIRED");
                lot.setPointsAvailable(0);
                loyaltyPointLotRepository.save(lot);
                continue;
            }

            LoyaltyAccount loyalty = loyaltyOpt.get();
            int nuevoDisponible = Math.max(0, safeInt(loyalty.getPuntosDisponibles()) - pointsToExpire);

            loyalty.setPuntosDisponibles(nuevoDisponible);
            loyalty.setFechaUltimoMovimiento(LocalDateTime.now());
            loyaltyAccountRepository.save(loyalty);

            LoyaltyMovement mov = new LoyaltyMovement();
            mov.setTenantId(lot.getTenantId());
            mov.setCustomerId(lot.getCustomerId());
            mov.setLoyaltyId(loyalty.getId());
            mov.setTipo("EXPIRE");
            mov.setOrigen("EXPIRATION");
            mov.setReferenciaId(lot.getId());
            mov.setDescripcion("Expiración automática de puntos");
            mov.setPuntos(-pointsToExpire);
            mov.setSaldoResultante(nuevoDisponible);
            mov.setCreadoPor(null);
            mov.setFechaCreacion(LocalDateTime.now());
            loyaltyMovementRepository.save(mov);

            lot.setPointsAvailable(0);
            lot.setStatus("EXPIRED");
            loyaltyPointLotRepository.save(lot);

            processed++;
        }

        return processed;
    }

    private int calcularPuntos(double total) {
        if (total <= 0) return 0;
        return (int) Math.floor(total * POINTS_PER_SOL);
    }

    private int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    private void markAppActivated(Customer customer) {
        if (!Boolean.TRUE.equals(customer.getAppActivated())) {
            customer.setAppActivated(true);
            customer.setAppActivatedAt(LocalDateTime.now());
            customer.setFechaActualizacion(LocalDateTime.now());
            customerRepository.save(customer);
        }
    }

    private LoyaltyAccount getOrCreateAccount(Tenant tenant, Customer customer) {
        return loyaltyAccountRepository
                .findByTenant_IdAndCustomer_Id(tenant.getId(), customer.getId())
                .orElseGet(() -> {
                    LoyaltyAccount loyalty = new LoyaltyAccount();
                    loyalty.setTenant(tenant);
                    loyalty.setCustomer(customer);
                    loyalty.setPuntosAcumulados(0);
                    loyalty.setPuntosDisponibles(0);
                    loyalty.setFechaUltimoMovimiento(LocalDateTime.now());
                    return loyaltyAccountRepository.save(loyalty);
                });
    }
}

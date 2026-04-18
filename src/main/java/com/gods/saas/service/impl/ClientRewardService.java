package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.response.RedeemRewardResponse;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import com.gods.saas.service.impl.impl.NotificationService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClientRewardService {

    private final CustomerRepository customerRepository;
    private final LoyaltyAccountRepository loyaltyAccountRepository;
    private final RewardItemRepository rewardItemRepository;
    private final RewardRedemptionRepository rewardRedemptionRepository;
    private final LoyaltyMovementRepository loyaltyMovementRepository;
    private final NotificationService notificationService;

    @Transactional
    public RedeemRewardResponse redeemReward(Authentication authentication, Long rewardId) {

        String idCustomer = authentication.getName();

        Customer customer = customerRepository.findById(Long.parseLong(idCustomer))
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));

        Long tenantId = customer.getTenant().getId();

        LoyaltyAccount loyalty = loyaltyAccountRepository
                .findByTenant_IdAndCustomer_Id(tenantId, customer.getId())
                .orElseThrow(() -> new RuntimeException("Cuenta de puntos no encontrada"));

        RewardItem reward = rewardItemRepository
                .findByIdAndTenant_IdAndActivoTrue(rewardId, tenantId)
                .orElseThrow(() -> new RuntimeException("Premio no encontrado o inactivo"));

        int puntosDisponibles = loyalty.getPuntosDisponibles() == null ? 0 : loyalty.getPuntosDisponibles();
        int puntosRequeridos = reward.getPuntosRequeridos() == null ? 0 : reward.getPuntosRequeridos();

        if (puntosDisponibles < puntosRequeridos) {
            throw new RuntimeException("No tienes puntos suficientes para este canje");
        }

        if (reward.getStock() != null && reward.getStock() <= 0) {
            throw new RuntimeException("Este premio no tiene stock disponible");
        }

        int nuevoSaldo = puntosDisponibles - puntosRequeridos;

        loyalty.setPuntosDisponibles(nuevoSaldo);
        loyalty.setFechaUltimoMovimiento(LocalDateTime.now());
        loyaltyAccountRepository.save(loyalty);

        if (reward.getStock() != null) {
            reward.setStock(reward.getStock() - 1);
            rewardItemRepository.save(reward);
        }

        String codigo = "GODS-" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 6)
                .toUpperCase();

        RewardRedemption redemption = RewardRedemption.builder()
                .tenantId(customer.getTenant().getId())
                .customerId(customer.getId())
                .rewardId(rewardId)
                .puntosUsados(puntosRequeridos)
                .estado("GENERATED")
                .codigo(codigo)
                .fechaCreacion(LocalDateTime.now())
                .build();

        rewardRedemptionRepository.save(redemption);

        LoyaltyMovement movement = LoyaltyMovement.builder()
                .tenantId(customer.getTenant().getId())
                .customerId(customer.getId())
                .loyaltyId(loyalty.getId())
                .tipo("REDEEM")
                .origen("REWARD")
                .referenciaId(redemption.getId())
                .descripcion("Canje de premio: " + reward.getNombre())
                .puntos(-puntosRequeridos)
                .saldoResultante(nuevoSaldo)
                .fechaCreacion(LocalDateTime.now())
                .build();

        loyaltyMovementRepository.save(movement);
        notificationService.notifyRewardRedeemed(redemption, customer, reward);
        return new RedeemRewardResponse(
                true,
                "Canje realizado correctamente",
                nuevoSaldo,
                codigo,
                redemption.getId()
        );
    }
}

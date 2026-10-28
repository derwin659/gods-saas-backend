package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.response.ClientRewardRedemptionResponse;
import com.gods.saas.domain.dto.response.RedeemRewardResponse;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import com.gods.saas.service.impl.impl.NotificationService;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClientRewardService {

    private final CustomerRepository customerRepository;
    private final LoyaltyAccountRepository loyaltyAccountRepository;
    private final RewardItemRepository rewardItemRepository;
    private final RewardRedemptionRepository rewardRedemptionRepository;
    private final LoyaltyMovementRepository loyaltyMovementRepository;
    private final LoyaltyLotConsumptionService lotConsumptionService;
    private final NotificationService notificationService;

    public List<ClientRewardRedemptionResponse> listRedemptions(Authentication authentication) {
        Customer customer = getAuthenticatedCustomer(authentication);
        Long tenantId = customer.getTenant().getId();

        return rewardRedemptionRepository
                .findByTenantIdAndCustomerIdOrderByFechaCreacionDesc(tenantId, customer.getId())
                .stream()
                .map(this::mapRedemption)
                .toList();
    }

    @Transactional(noRollbackFor=RewardUnavailableException.class)
    public RedeemRewardResponse redeemReward(Authentication authentication, Long rewardId) {
        return redeem(getAuthenticatedCustomer(authentication),rewardId,null);
    }

    public static class RewardUnavailableException extends RuntimeException {
        public RewardUnavailableException(String message) { super(message); }
    }

    @Transactional(noRollbackFor=RewardUnavailableException.class)
    public RedeemRewardResponse redeemForWhatsapp(Long tenantId,Long customerId,Long rewardId,int expectedPoints) {
        Customer customer=customerRepository.findByIdAndTenantIdWithTenant(customerId,tenantId)
                .filter(c->c.isPhoneVerified() && !Boolean.FALSE.equals(c.getActivo())
                        && c.getWhatsappOptedOutAt()==null && Boolean.TRUE.equals(c.getWhatsappTransactionalEnabled()))
                .orElseThrow(()->new RewardUnavailableException("Verifica tu cuenta y permisos de WhatsApp antes de canjear."));
        return redeem(customer,rewardId,expectedPoints);
    }

    private RedeemRewardResponse redeem(Customer customer,Long rewardId,Integer expectedPoints) {
        Long tenantId=customer.getTenant().getId();
        LoyaltyAccount loyalty = loyaltyAccountRepository
                .findForRedemption(tenantId, customer.getId())
                .orElseThrow(() -> new RewardUnavailableException("Cuenta de puntos no encontrada"));

        RewardItem reward = rewardItemRepository
                .findForRedemption(rewardId, tenantId)
                .orElseThrow(() -> new RewardUnavailableException("Premio no encontrado o inactivo"));

        if (reward.getPuntosRequeridos()==null || reward.getPuntosRequeridos()<0)
            throw new RewardUnavailableException("El negocio debe revisar los puntos de ese premio.");
        if (expectedPoints!=null && !expectedPoints.equals(reward.getPuntosRequeridos()))
            throw new RewardUnavailableException("Los puntos requeridos cambiaron. Consulta PREMIOS y confirma de nuevo.");
        int puntosDisponibles = loyalty.getPuntosDisponibles() == null ? 0 : loyalty.getPuntosDisponibles();
        int puntosRequeridos = reward.getPuntosRequeridos() == null ? 0 : reward.getPuntosRequeridos();

        if (puntosDisponibles < puntosRequeridos) {
            throw new RewardUnavailableException("No tienes puntos suficientes para este canje");
        }

        if (reward.getStock() != null && reward.getStock() <= 0) {
            throw new RewardUnavailableException("Este premio no tiene stock disponible");
        }

        int nuevoSaldo = puntosDisponibles - puntosRequeridos;

        lotConsumptionService.consume(tenantId, customer.getId(), puntosRequeridos);
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

    private Customer getAuthenticatedCustomer(Authentication authentication) {
        String idCustomer = authentication.getName();

        return customerRepository.findById(Long.parseLong(idCustomer))
                .orElseThrow(() -> new RewardUnavailableException("Cliente no encontrado"));
    }

    private ClientRewardRedemptionResponse mapRedemption(RewardRedemption redemption) {
        RewardItem reward = rewardItemRepository.findById(redemption.getRewardId()).orElse(null);
        String estado = redemption.getEstado();
        boolean used = "USED".equalsIgnoreCase(estado);
        boolean available = "GENERATED".equalsIgnoreCase(estado);

        return ClientRewardRedemptionResponse.builder()
                .redemptionId(redemption.getId())
                .codigo(redemption.getCodigo())
                .estado(estado)
                .usado(used)
                .disponibleParaUsar(available)
                .puntosUsados(redemption.getPuntosUsados())
                .fechaCreacion(redemption.getFechaCreacion())
                .fechaUso(redemption.getFechaUso())
                .rewardId(reward != null ? reward.getId() : redemption.getRewardId())
                .rewardNombre(reward != null ? reward.getNombre() : null)
                .rewardDescripcion(reward != null ? reward.getDescripcion() : null)
                .rewardImagenUrl(reward != null ? reward.getImagenUrl() : null)
                .build();
    }
}

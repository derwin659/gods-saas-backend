package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.response.ClientPromotionResponse;
import com.gods.saas.domain.model.Customer;
import com.gods.saas.domain.model.LoyaltyAccount;
import com.gods.saas.domain.model.Promotion;
import com.gods.saas.domain.repository.CustomerRepository;
import com.gods.saas.domain.repository.LoyaltyAccountRepository;
import com.gods.saas.domain.repository.PromotionRepository;
import com.gods.saas.service.impl.impl.PromotionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PromotionServiceImpl implements PromotionService {

    private final CustomerRepository customerRepository;
    private final LoyaltyAccountRepository loyaltyAccountRepository;
    private final PromotionRepository promotionRepository;

    @Override
    public List<ClientPromotionResponse> getClientPromotions(String idCustomer) {
        System.out.println("ID CUSTOMER RECIBIDO = " + idCustomer);

        Customer customer = customerRepository.findById(Long.parseLong(idCustomer))
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));

        Long tenantId = customer.getTenant().getId();

        LoyaltyAccount loyalty = loyaltyAccountRepository
                .findByTenant_IdAndCustomer_Id(tenantId, customer.getId())
                .orElse(null);

        int puntosDisponibles = loyalty != null && loyalty.getPuntosDisponibles() != null
                ? loyalty.getPuntosDisponibles()
                : 0;

        System.out.println("CUSTOMER ENCONTRADO = " + customer.getId());
        System.out.println("TENANT DEL CUSTOMER = " + tenantId);
        System.out.println("PUNTOS DISPONIBLES = " + puntosDisponibles);

        List<Promotion> promotions = promotionRepository.findActiveClientPromotions(
                tenantId,
                puntosDisponibles
        );

        System.out.println("PROMOCIONES ENCONTRADAS = " + promotions.size());

        return promotions.stream()
                .map(this::toResponse)
                .toList();
    }
    private ClientPromotionResponse toResponse(Promotion p) {
        return new ClientPromotionResponse(
                p.getId(),
                p.getTitulo(),
                p.getSubtitulo(),
                p.getDescripcion(),
                p.getBadge(),
                p.getTipo() != null ? p.getTipo().name() : null,
                p.getIconName(),
                p.getImageUrl(),
                p.getPriceText(),
                p.getCtaLabel(),
                p.getRedirectType() != null ? p.getRedirectType().name() : "NONE",
                p.getRedirectValue(),
                p.isDestacado()
        );
    }
}

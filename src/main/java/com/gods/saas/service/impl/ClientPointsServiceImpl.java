package com.gods.saas.service.impl;


import com.gods.saas.domain.dto.response.ClientPointsResponse;
import com.gods.saas.domain.dto.response.PointMovementResponse;
import com.gods.saas.domain.dto.response.PointsSummaryResponse;
import com.gods.saas.domain.dto.response.RewardItemResponse;
import com.gods.saas.domain.model.Customer;
import com.gods.saas.domain.model.LoyaltyAccount;
import com.gods.saas.domain.repository.CustomerRepository;
import com.gods.saas.domain.repository.LoyaltyAccountRepository;
import com.gods.saas.domain.repository.LoyaltyMovementRepository;
import com.gods.saas.domain.repository.RewardItemRepository;
import com.gods.saas.service.impl.impl.ClientPointsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ClientPointsServiceImpl implements ClientPointsService {



    private final CustomerRepository customerRepository;
    private final LoyaltyAccountRepository loyaltyAccountRepository;
    private final LoyaltyMovementRepository loyaltyMovementRepository;
    private final RewardItemRepository rewardItemRepository;

    @Override
    public ClientPointsResponse getClientPoints(Authentication authentication) {

        String idCustomer = authentication.getName();
        System.out.println("PHONE/USERNAME RECIBIDO = " + idCustomer);

        Customer customer = customerRepository.findById(Long.parseLong(idCustomer))
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));

        Long tenantId = customer.getTenant().getId();

        LoyaltyAccount loyalty = loyaltyAccountRepository
                .findByTenant_IdAndCustomer_Id(tenantId, customer.getId())
                .orElseGet(this::buildEmptyAccount);

        int disponibles = safeInt(loyalty.getPuntosDisponibles());

        var rewards = rewardItemRepository
                .findByTenantIdAndActivoTrueOrderByPuntosRequeridosAsc(tenantId);

        int metaCorteGratis = rewards.stream()
                .filter(r -> r.getNombre() != null
                        && r.getNombre().trim().toLowerCase().contains("corte gratis"))
                .map(r -> safeInt(r.getPuntosRequeridos()))
                .findFirst()
                .orElse(0);

        int faltanParaCorteGratis = Math.max(0, metaCorteGratis - disponibles);
        double progresoCorteGratis = metaCorteGratis <= 0
                ? 0.0
                : Math.min(1.0, disponibles / (double) metaCorteGratis);

        var premioDisponible = rewards.stream()
                .filter(r -> safeInt(r.getPuntosRequeridos()) <= disponibles)
                .reduce((first, second) -> second)
                .orElse(null);

        boolean puedeCanjearAlgo = premioDisponible != null;
        String premioDisponibleAhora = premioDisponible != null ? premioDisponible.getNombre() : null;
        Integer puntosPremioDisponible = premioDisponible != null
                ? safeInt(premioDisponible.getPuntosRequeridos())
                : null;

        PointsSummaryResponse summary = new PointsSummaryResponse(
                disponibles,
                metaCorteGratis,
                faltanParaCorteGratis,
                progresoCorteGratis,
                puedeCanjearAlgo,
                premioDisponibleAhora,
                puntosPremioDisponible
        );

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM");

        List<PointMovementResponse> movimientos = loyaltyMovementRepository
                .findTop10ByTenantIdAndCustomerIdOrderByFechaCreacionDesc(tenantId, customer.getId())
                .stream()
                .map(m -> new PointMovementResponse(
                        m.getFechaCreacion() != null ? m.getFechaCreacion().format(formatter) : "",
                        m.getDescripcion() != null ? m.getDescripcion() : "Movimiento de puntos",
                        Math.abs(safeInt(m.getPuntos())),
                        safeInt(m.getPuntos()) >= 0
                ))
                .toList();

        List<RewardItemResponse> premios = rewards.stream()
                .map(r -> new RewardItemResponse(
                        r.getId(),
                        r.getNombre(),
                        r.getDescripcion(),
                        safeInt(r.getPuntosRequeridos()),
                        r.getNombre() != null
                                && r.getNombre().trim().toLowerCase().contains("corte gratis")
                ))
                .toList();

        return new ClientPointsResponse(summary, movimientos, premios);
    }
    private LoyaltyAccount buildEmptyAccount() {
        LoyaltyAccount a = new LoyaltyAccount();
        a.setPuntosDisponibles(0);
        a.setPuntosAcumulados(0);
        return a;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }


}
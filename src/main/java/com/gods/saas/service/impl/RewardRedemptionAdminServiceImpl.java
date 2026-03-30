package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.response.RewardRedemptionDetailResponse;
import com.gods.saas.domain.dto.response.UseRewardRedemptionResponse;
import com.gods.saas.domain.model.Customer;
import com.gods.saas.domain.model.RewardItem;
import com.gods.saas.domain.model.RewardRedemption;
import com.gods.saas.domain.repository.AppUserRepository;
import com.gods.saas.domain.repository.BranchRepository;
import com.gods.saas.domain.repository.CustomerRepository;
import com.gods.saas.domain.repository.RewardItemRepository;
import com.gods.saas.domain.repository.RewardRedemptionRepository;
import com.gods.saas.security.AuthHelper;
import com.gods.saas.security.AuthUserInfo;
import com.gods.saas.service.impl.impl.RewardRedemptionAdminService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RewardRedemptionAdminServiceImpl implements RewardRedemptionAdminService {

    private final RewardRedemptionRepository rewardRedemptionRepository;
    private final CustomerRepository customerRepository;
    private final RewardItemRepository rewardItemRepository;
    private final BranchRepository branchRepository;
    private final AppUserRepository appUserRepository;
    private final AuthHelper authHelper;

    private static final Set<String> ALLOWED_ROLES = Set.of("OWNER", "ADMIN", "BARBER");

    @Override
    public RewardRedemptionDetailResponse findByCode(String codigo, Authentication authentication) {
        String code = codigo == null ? "" : codigo.trim();

        System.out.println("codigo recibido => [" + codigo + "]");
        System.out.println("codigo normalizado => [" + code + "]");
        System.out.println("authentication null? => " + (authentication == null));

        if (code.isEmpty()) {
            throw new RuntimeException("El código es obligatorio");
        }

        Long tenantId = null;

        if (authentication != null) {
            AuthUserInfo auth = authHelper.getUserInfo(authentication);

            System.out.println("auth userId => " + (auth != null ? auth.getUserId() : null));
            System.out.println("auth role => " + (auth != null ? auth.getRole() : null));
            System.out.println("auth tenantId => " + (auth != null ? auth.getTenantId() : null));
            System.out.println("auth branchId => " + (auth != null ? auth.getBranchId() : null));

            tenantId = auth != null ? auth.getTenantId() : null;
        }

        RewardRedemption redemption;

        if (tenantId != null) {
            redemption = rewardRedemptionRepository
                    .findByTenantIdAndCodigoIgnoreCase(tenantId, code)
                    .orElseThrow(() -> new RuntimeException("Código no encontrado"));
        } else {
            redemption = rewardRedemptionRepository
                    .findByCodigoIgnoreCase(code)
                    .orElseThrow(() -> new RuntimeException("Código no encontrado"));
        }

        return mapToDetail(redemption);
    }

    @Override
    @Transactional
    public UseRewardRedemptionResponse useRedemption(Long redemptionId, Authentication authentication) {
        AuthUserInfo auth = authHelper.getUserInfo(authentication);
        System.out.printf("PORCENTAJE");
        Long userId = auth.getUserId();
        //validateRole(auth);

        /*Long tenantId = auth.getTenantId();
        Long userId = auth.getUserId();
        Long branchId = auth.getBranchId();

        if (tenantId == null) {
            throw new RuntimeException("No se pudo determinar el tenant del usuario");
        }*/

        RewardRedemption redemption = rewardRedemptionRepository
                .findById(redemptionId)
                .orElseThrow(() -> new RuntimeException("Canje no encontrado"));

        if ("USED".equalsIgnoreCase(redemption.getEstado())) {
            return UseRewardRedemptionResponse.builder()
                    .success(false)
                    .message("Este código ya fue usado")
                    .redemptionId(redemption.getId())
                    .codigo(redemption.getCodigo())
                    .estado(redemption.getEstado())
                    .build();
        }

        if ("CANCELLED".equalsIgnoreCase(redemption.getEstado())) {
            return UseRewardRedemptionResponse.builder()
                    .success(false)
                    .message("Este código está cancelado")
                    .redemptionId(redemption.getId())
                    .codigo(redemption.getCodigo())
                    .estado(redemption.getEstado())
                    .build();
        }

        redemption.setEstado("USED");
        redemption.setFechaUso(LocalDateTime.now());



        if (userId != null && appUserRepository.existsById(userId)) {
            redemption.setUsadoPorUserId(userId);
        }


        rewardRedemptionRepository.save(redemption);

        return UseRewardRedemptionResponse.builder()
                .success(true)
                .message("Canje aplicado correctamente")
                .redemptionId(redemption.getId())
                .codigo(redemption.getCodigo())
                .estado(redemption.getEstado())
                .build();
    }



    private RewardRedemptionDetailResponse mapToDetail(RewardRedemption r) {
        Customer customer = customerRepository.findById(r.getCustomerId()).orElse(null);
        RewardItem rewardItem = rewardItemRepository.findById(r.getRewardId()).orElse(null);

        String nombres = customer != null && customer.getNombres() != null
                ? customer.getNombres().trim()
                : "";

        String apellidos = customer != null && customer.getApellidos() != null
                ? customer.getApellidos().trim()
                : "";

        String fullName = (nombres + " " + apellidos).trim();

        return RewardRedemptionDetailResponse.builder()
                .redemptionId(r.getId())
                .codigo(r.getCodigo())
                .estado(r.getEstado())
                .puntosUsados(r.getPuntosUsados())
                .fechaCreacion(r.getFechaCreacion())
                .fechaUso(r.getFechaUso())
                .customerId(customer != null ? customer.getId() : r.getCustomerId())
                .customerNombreCompleto(fullName.isBlank() ? "Cliente" : fullName)
                .customerTelefono(customer != null ? customer.getTelefono() : null)
                .rewardId(rewardItem != null ? rewardItem.getId() : r.getRewardId())
                .rewardNombre(rewardItem != null ? rewardItem.getNombre() : null)
                .rewardDescripcion(rewardItem != null ? rewardItem.getDescripcion() : null)
                .usadoEnBranchId(r.getUsadoEnBranchId())
                .usadoPorUserId(r.getUsadoPorUserId())
                .build();
    }
}
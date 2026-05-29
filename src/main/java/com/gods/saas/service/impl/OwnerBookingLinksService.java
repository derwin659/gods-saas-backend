package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.response.OwnerBookingLinksResponse;
import com.gods.saas.domain.model.AppUser;
import com.gods.saas.domain.model.Branch;
import com.gods.saas.domain.model.Tenant;
import com.gods.saas.domain.repository.AppUserRepository;
import com.gods.saas.domain.repository.BranchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OwnerBookingLinksService {

    private static final String PUBLIC_WEB_BASE_URL = "https://www.supergodsapp.com/reservar";

    private final AppUserRepository appUserRepository;
    private final BranchRepository branchRepository;

    public OwnerBookingLinksResponse getOwnerBookingLinks() {
        AppUser user = getCurrentUser();

        Tenant tenant = user.getTenant();
        if (tenant == null) {
            throw new RuntimeException("El usuario no tiene negocio asignado.");
        }

        String codigoNegocio = tenant.getCodigo();
        if (codigoNegocio == null || codigoNegocio.trim().isEmpty()) {
            throw new RuntimeException("El negocio no tiene código configurado.");
        }

        String encodedCode = URLEncoder.encode(codigoNegocio.trim(), StandardCharsets.UTF_8);
        String businessLink = PUBLIC_WEB_BASE_URL + "/" + encodedCode;

        List<Branch> branches = branchRepository.findByTenant_IdAndActivoTrue(tenant.getId());

        List<OwnerBookingLinksResponse.BranchBookingLinkResponse> branchLinks = branches.stream()
                .map(branch -> OwnerBookingLinksResponse.BranchBookingLinkResponse.builder()
                        .branchId(branch.getId())
                        .branchName(branch.getNombre())
                        .bookingLink(businessLink + "?branchId=" + branch.getId())
                        .build()
                )
                .toList();

        return OwnerBookingLinksResponse.builder()
                .codigoNegocio(codigoNegocio)
                .tenantId(tenant.getId())
                .tenantName(tenant.getNombre())
                .businessLink(businessLink)
                .branches(branchLinks)
                .build();
    }

    private AppUser getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || auth.getName() == null || auth.getName().trim().isEmpty()) {
            throw new RuntimeException("Sesión no válida.");
        }

        String email = auth.getName().trim();

        return appUserRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new RuntimeException("Usuario autenticado no encontrado."));
    }
}

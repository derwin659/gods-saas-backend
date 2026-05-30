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
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OwnerBookingLinksService {

    private static final String PUBLIC_WEB_BASE_URL = "https://www.supergodsapp.com/reservar";

    private final AppUserRepository appUserRepository;
    private final BranchRepository branchRepository;

    public OwnerBookingLinksResponse getOwnerBookingLinks() {
        AppUser currentUser = resolveCurrentUser();

        Tenant tenant = currentUser.getTenant();
        if (tenant == null) {
            throw new RuntimeException("El usuario autenticado no tiene negocio asignado.");
        }

        String codigoNegocio = tenant.getCodigo();
        if (codigoNegocio == null || codigoNegocio.trim().isEmpty()) {
            throw new RuntimeException("El negocio no tiene código configurado.");
        }

        String cleanCode = codigoNegocio.trim();
        String businessLink = PUBLIC_WEB_BASE_URL + "/" + cleanCode;

        List<Branch> branches = branchRepository.findByTenant_IdAndActivoTrue(tenant.getId());

        List<OwnerBookingLinksResponse.BranchBookingLink> branchLinks = branches.stream()
                .map(branch -> OwnerBookingLinksResponse.BranchBookingLink.builder()
                        .branchId(branch.getId())
                        .branchName(branch.getNombre())
                        .bookingLink(businessLink + "?branchId=" + branch.getId())
                        .build())
                .toList();

        return OwnerBookingLinksResponse.builder()
                .tenantId(tenant.getId())
                .tenantName(tenant.getNombre())
                .codigoNegocio(cleanCode)
                .businessLink(businessLink)
                .branches(branchLinks)
                .build();
    }

    private AppUser resolveCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Usuario autenticado no encontrado.");
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof AppUser appUser) {
            return appUserRepository.findByIdWithTenant(appUser.getId())
                    .or(() -> appUserRepository.findById(appUser.getId()))
                    .orElseThrow(() -> new RuntimeException("Usuario autenticado no encontrado."));
        }

        String identifier = null;

        if (principal instanceof UserDetails userDetails) {
            identifier = userDetails.getUsername();
        }

        if ((identifier == null || identifier.isBlank()) && authentication.getName() != null) {
            identifier = authentication.getName();
        }

        if (identifier == null || identifier.isBlank() || "anonymousUser".equalsIgnoreCase(identifier)) {
            throw new RuntimeException("Usuario autenticado no encontrado.");
        }

        String value = identifier.trim();

        // En algunos JWT el subject puede venir como ID numérico.
        try {
            Long userId = Long.parseLong(value);
            return appUserRepository.findByIdWithTenant(userId)
                    .or(() -> appUserRepository.findById(userId))
                    .orElseThrow(() -> new RuntimeException("Usuario autenticado no encontrado."));
        } catch (NumberFormatException ignored) {
            // Si no es número, se intenta como email.
        }

        return appUserRepository.findByEmailIgnoreCase(value)
                .orElseThrow(() -> new RuntimeException("Usuario autenticado no encontrado."));
    }
}

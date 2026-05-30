package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.response.BarberBookingLinkResponse;
import com.gods.saas.domain.model.AppUser;
import com.gods.saas.domain.model.Branch;
import com.gods.saas.domain.model.Tenant;
import com.gods.saas.domain.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BarberBookingLinkService {

    private static final String PUBLIC_WEB_BASE_URL = "https://www.supergodsapp.com/reservar";

    private final AppUserRepository appUserRepository;

    @Transactional(readOnly = true)
    public BarberBookingLinkResponse getBookingLink() {
        AppUser barber = resolveCurrentUser();

        Tenant tenant = barber.getTenant();
        if (tenant == null) {
            throw new RuntimeException("El barbero no tiene negocio asignado.");
        }

        Branch branch = barber.getBranch();
        if (branch == null) {
            throw new RuntimeException("El barbero no tiene sede asignada.");
        }

        String codigoNegocio = tenant.getCodigo();
        if (codigoNegocio == null || codigoNegocio.trim().isEmpty()) {
            throw new RuntimeException("El negocio no tiene código configurado.");
        }

        String cleanCode = codigoNegocio.trim();

        String bookingLink = PUBLIC_WEB_BASE_URL
                + "/" + cleanCode
                + "?branchId=" + branch.getId()
                + "&barberId=" + barber.getId();

        String barberName = (
                (barber.getNombre() != null ? barber.getNombre() : "") + " " +
                (barber.getApellido() != null ? barber.getApellido() : "")
        ).trim();

        return BarberBookingLinkResponse.builder()
                .bookingLink(bookingLink)
                .codigoNegocio(cleanCode)
                .tenantId(tenant.getId())
                .branchId(branch.getId())
                .barberId(barber.getId())
                .tenantName(tenant.getNombre())
                .branchName(branch.getNombre())
                .barberName(barberName.isEmpty() ? barber.getEmail() : barberName)
                .build();
    }

    private AppUser resolveCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Usuario autenticado no encontrado.");
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof AppUser appUser) {
            return appUserRepository.findById(appUser.getId())
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

        try {
            Long userId = Long.parseLong(value);
            return appUserRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("Usuario autenticado no encontrado."));
        } catch (NumberFormatException ignored) {
            // Si no es ID, se busca por email.
        }

        return appUserRepository.findByEmailIgnoreCase(value)
                .orElseThrow(() -> new RuntimeException("Usuario autenticado no encontrado."));
    }
}

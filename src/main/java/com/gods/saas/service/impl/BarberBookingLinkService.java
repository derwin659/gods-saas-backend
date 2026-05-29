package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.response.BarberBookingLinkResponse;
import com.gods.saas.domain.model.AppUser;
import com.gods.saas.domain.model.Branch;
import com.gods.saas.domain.model.Tenant;
import com.gods.saas.domain.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BarberBookingLinkService {

    private static final String PUBLIC_WEB_BASE_URL = "https://www.supergodsapp.com/reservar";

    private final AppUserRepository appUserRepository;

    @Transactional(readOnly = true)
    public BarberBookingLinkResponse getBookingLink() {
        AppUser barber = getCurrentUser();

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

        String bookingLink = PUBLIC_WEB_BASE_URL
                + "/" + codigoNegocio.trim()
                + "?branchId=" + branch.getId()
                + "&barberId=" + barber.getId();

        return BarberBookingLinkResponse.builder()
                .bookingLink(bookingLink)
                .codigoNegocio(codigoNegocio)
                .tenantId(tenant.getId())
                .branchId(branch.getId())
                .barberId(barber.getId())
                .tenantName(tenant.getNombre())
                .branchName(branch.getNombre())
                .barberName(buildUserFullName(barber))
                .build();
    }

    private AppUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Sesión no válida.");
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof AppUser currentUser) {
            return appUserRepository.findById(currentUser.getId())
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado."));
        }

        String email = authentication.getName();
        if (email == null || email.trim().isEmpty()) {
            throw new RuntimeException("No se pudo identificar al usuario actual.");
        }

        return appUserRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado."));
    }

    private String buildUserFullName(AppUser user) {
        String nombre = user.getNombre() == null ? "" : user.getNombre().trim();
        String apellido = user.getApellido() == null ? "" : user.getApellido().trim();
        String fullName = (nombre + " " + apellido).trim();
        return fullName.isBlank() ? user.getEmail() : fullName;
    }
}

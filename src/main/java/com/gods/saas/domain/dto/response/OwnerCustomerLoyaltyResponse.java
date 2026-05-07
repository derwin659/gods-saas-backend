package com.gods.saas.domain.dto.response;


public record OwnerCustomerLoyaltyResponse(
        Long customerId,
        String nombres,
        String apellidos,
        String telefono,
        Integer puntosDisponibles,
        Integer puntosAcumulados,
        Boolean migrated,
        Boolean appActivated
) {
}
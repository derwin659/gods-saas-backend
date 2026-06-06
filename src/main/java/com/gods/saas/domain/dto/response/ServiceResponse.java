package com.gods.saas.domain.dto.response;

public record ServiceResponse(
        Long serviceId,
        Long tenantId,
        String nombre,
        String descripcion,
        Integer duracionMinutos,
        Double precio,
        Boolean precioVariable,
        String categoria,
        Boolean activo,
        String imageUrl
) {
}

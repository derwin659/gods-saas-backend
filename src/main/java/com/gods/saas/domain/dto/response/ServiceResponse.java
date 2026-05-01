package com.gods.saas.domain.dto.response;

public record ServiceResponse(
        Long serviceId,
        Long tenantId,
        String nombre,
        String descripcion,
        Integer duracionMinutos,
        Double precio,
        String categoria,
        Boolean activo,
        String imageUrl
) {
}
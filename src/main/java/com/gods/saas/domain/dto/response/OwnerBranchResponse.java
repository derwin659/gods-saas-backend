package com.gods.saas.domain.dto.response;

public record OwnerBranchResponse(
        Long branchId,
        String nombre,
        String direccion,
        String telefono,
        Boolean activo,
        String imageUrl
) {
}
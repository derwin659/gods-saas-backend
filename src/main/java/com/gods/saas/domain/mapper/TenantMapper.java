package com.gods.saas.domain.mapper;

import com.gods.saas.domain.dto.TenantDto;
import com.gods.saas.domain.model.Tenant;


public class TenantMapper {

    public static TenantDto toDto(Tenant t) {
        return TenantDto.builder()
                .id(t.getId())
                .name(t.getNombre())
                .country(t.getPais())
                .city(t.getCiudad())
                .ownerName(t.getOwnerName())
                .plan(t.getPlan())
                .status(Boolean.TRUE.equals(t.getActive()) ? "ACTIVA" : "SUSPENDED")
                .codigo(t.getCodigo())
                .logoUrl(t.getLogoUrl())
                .createdAt(t.getFechaCreacion() != null ? t.getFechaCreacion().toString() : null)
                .build();
    }
}

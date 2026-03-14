package com.gods.saas.domain.mapper;

import com.gods.saas.domain.dto.TenantDto;
import com.gods.saas.domain.model.Tenant;


public class TenantMapper {

    public static TenantDto toDto(Tenant t) {
        return new TenantDto(
                t.getId(),
                t.getNombre(),
                t.getPais(),
                t.getCiudad(),
                t.getOwnerName(),
                t.getCodigo(),
                t.getPlan(),
                t.getActive() ? "ACTIVE" : "SUSPENDED",
                t.getFechaCreacion().toString()
        );
    }
}

package com.gods.saas.domain.dto;

import com.gods.saas.domain.model.AppUser;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AppUserResponse {
    private Long id;
    private String nombre;
    private String apellido;
    private String email;
    private String phone;
    private String rol;
    private Boolean activo;
    private Long branchId;

    public static AppUserResponse from(AppUser user) {
        return AppUserResponse.builder()
                .id(user.getId())
                .nombre(user.getNombre())
                .apellido(user.getApellido())
                .email(user.getEmail())
                .phone(user.getPhone())
                .rol(user.getRol())
                .activo(user.getActivo())
                .branchId(
                        user.getBranch() != null ? user.getBranch().getId() : null
                )
                .build();
    }
}



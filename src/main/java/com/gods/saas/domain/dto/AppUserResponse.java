package com.gods.saas.domain.dto;

import com.gods.saas.domain.model.AppUser;
import lombok.Builder;
import lombok.Data;
import java.util.List;

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
    private Boolean canSell;
    private Boolean professionalProfileEnabled;
    private Long branchId;
    private List<Long> branchIds;
    private List<String> branchNames;

    public static AppUserResponse from(AppUser user) {
        return AppUserResponse.builder()
                .id(user.getId())
                .nombre(user.getNombre())
                .apellido(user.getApellido())
                .email(user.getEmail())
                .phone(user.getPhone())
                .rol(user.getRol())
                .activo(user.getActivo())
                .canSell(user.getCanSell())
                .professionalProfileEnabled(false)
                .branchId(
                        user.getBranch() != null ? user.getBranch().getId() : null
                )
                .build();
    }
}



package com.gods.saas.domain.dto.response;

import com.gods.saas.domain.model.AppUser;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BarberMiniResponse {
    private Long id;
    private String nombre;
    private String apellido;
    private Long branchId;
    private String photoUrl;

    public static BarberMiniResponse fromEntity(AppUser u) {
        return BarberMiniResponse.builder()
                .id(u.getId())
                .nombre(u.getNombre())
                .apellido(u.getApellido())
                .branchId(u.getBranch() != null ? u.getBranch().getId() : null)
                .photoUrl(u.getPhotoUrl())
                .build();
    }
}
package com.gods.saas.domain.dto.response;

import com.gods.saas.domain.model.AppUser;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BarberMiniResponse {
    private Long id;
    private String nombre;
    private String apellido;
    private Long branchId;
    private List<Long> branchIds;
    private String photoUrl;

    public static BarberMiniResponse fromEntity(AppUser u) {
        return fromEntity(u, List.of());
    }

    public static BarberMiniResponse fromEntity(AppUser u, List<Long> branchIds) {
        List<Long> safeBranchIds = branchIds == null ? List.of() : branchIds;

        return BarberMiniResponse.builder()
                .id(u.getId())
                .nombre(u.getNombre())
                .apellido(u.getApellido())
                .branchId(safeBranchIds.isEmpty() ? null : safeBranchIds.get(0))
                .branchIds(safeBranchIds)
                .photoUrl(u.getPhotoUrl())
                .build();
    }
}
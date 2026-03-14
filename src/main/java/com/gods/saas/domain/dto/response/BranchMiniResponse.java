package com.gods.saas.domain.dto.response;

import com.gods.saas.domain.model.Branch;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchMiniResponse {
    private Long id;
    private String nombre;
    private String direccion;

    public static BranchMiniResponse fromEntity(Branch b) {
        return BranchMiniResponse.builder()
                .id(b.getId())
                .nombre(b.getNombre())
                .direccion(b.getDireccion())
                .build();
    }
}

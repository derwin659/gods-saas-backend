package com.gods.saas.domain.dto.response;

import com.gods.saas.domain.model.ServiceEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceMiniResponse {
    private Long id;
    private String nombre;
    private Integer duracionMinutos;
    private Double precio;
    private String imageUrl;

    public static ServiceMiniResponse fromEntity(ServiceEntity s) {
        return ServiceMiniResponse.builder()
                .id(s.getId())
                .nombre(s.getNombre())
                .duracionMinutos(s.getDuracionMinutos())
                .precio(s.getPrecio())
                .imageUrl(s.getImageUrl())
                .build();
    }
}
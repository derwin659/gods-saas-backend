package com.gods.saas.domain.dto.response;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BarberServiceResponse {
    private Long id;
    private String nombre;
    private String descripcion;
    private Integer duracionMin;
    private Double precio;
    private Boolean precioVariable;
    private String categoria;
    private String imageUrl;
    private Boolean activo;
}

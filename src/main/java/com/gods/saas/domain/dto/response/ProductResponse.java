package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ProductResponse {
    private Long id;
    private String nombre;
    private String sku;
    private String descripcion;
    private BigDecimal precioCompra;
    private BigDecimal precioVenta;
    private Double precio;
    private BigDecimal barberCommissionAmount;
    private Integer stockActual;
    private Integer stockMinimo;
    private String categoria;
    private Boolean activo;
    private Boolean permiteVentaSinStock;
    private Boolean stockBajo;
}

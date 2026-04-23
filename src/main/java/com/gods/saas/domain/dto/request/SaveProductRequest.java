package com.gods.saas.domain.dto.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SaveProductRequest {

    private String nombre;
    private String sku;
    private String descripcion;
    private BigDecimal precioCompra;
    private BigDecimal precioVenta;

    /**
     * Compatibilidad con el campo legacy actual.
     * Si mandan "precio" y no "precioVenta", se toma ese valor.
     */
    private BigDecimal precio;

    private Integer stockActual;
    private Integer stockMinimo;
    private String categoria;
    private Boolean activo;
    private Boolean permiteVentaSinStock;
}

package com.gods.saas.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "product")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(length = 80)
    private String sku;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    /**
     * Campo legacy que ya existe en producción.
     * Se mantiene por compatibilidad con el código actual.
     */
    @Column(nullable = false)
    private Double precio;

    /**
     * Nuevo: costo de compra del producto.
     */
    @Column(name = "precio_compra", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal precioCompra = BigDecimal.ZERO;

    /**
     * Nuevo: precio de venta recomendado.
     * Si viene null o 0, el sistema seguirá usando el campo legacy "precio".
     */
    @Column(name = "precio_venta", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal precioVenta = BigDecimal.ZERO;

    @Column(name = "stock_actual", nullable = false)
    @Builder.Default
    private Integer stockActual = 0;

    @Column(name = "stock_minimo", nullable = false)
    @Builder.Default
    private Integer stockMinimo = 0;

    @Column(name = "permite_venta_sin_stock", nullable = false)
    @Builder.Default
    private Boolean permiteVentaSinStock = false;

    @Column(length = 100)
    private String categoria;

    @Builder.Default
    private Boolean activo = true;

    @PrePersist
    @PreUpdate
    private void syncLegacyPriceFields() {
        if (precioVenta == null) {
            precioVenta = BigDecimal.ZERO;
        }
        if (precioCompra == null) {
            precioCompra = BigDecimal.ZERO;
        }
        if (stockActual == null) {
            stockActual = 0;
        }
        if (stockMinimo == null) {
            stockMinimo = 0;
        }
        if (permiteVentaSinStock == null) {
            permiteVentaSinStock = false;
        }
        if (activo == null) {
            activo = true;
        }

        // Compatibilidad bidireccional entre "precio" y "precioVenta"
        if ((precio == null || precio <= 0) && precioVenta.compareTo(BigDecimal.ZERO) > 0) {
            precio = precioVenta.doubleValue();
        }
        if ((precioVenta == null || precioVenta.compareTo(BigDecimal.ZERO) <= 0) && precio != null && precio > 0) {
            precioVenta = BigDecimal.valueOf(precio);
        }
    }
}
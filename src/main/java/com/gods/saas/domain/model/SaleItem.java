package com.gods.saas.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "sale_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sale_item_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private ServiceEntity service;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    /**
     * Barbero que realizó este item.
     * Muy útil para liquidaciones.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "barber_user_id")
    private AppUser barberUser;

    @Column(name = "tipo_item", length = 20, nullable = false)
    @Builder.Default
    private String tipoItem = "SERVICE"; // SERVICE / PRODUCT

    @Column(name = "nombre_item", length = 150, nullable = false)
    @Builder.Default
    private String nombreItem = "Item";

    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;

    @Column(name = "precio_unitario", precision = 12, scale = 2, nullable = false)
    private BigDecimal precioUnitario;

    @Column(name = "costo_unitario", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal costoUnitario = BigDecimal.ZERO;

    @Column(name = "subtotal", precision = 12, scale = 2, nullable = false)
    private BigDecimal subtotal;

    @Column(name = "ganancia", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal ganancia = BigDecimal.ZERO;

    @PrePersist
    @PreUpdate
    private void ensureDefaults() {
        if (tipoItem == null || tipoItem.isBlank()) {
            tipoItem = product != null ? "PRODUCT" : "SERVICE";
        }

        if ((nombreItem == null || nombreItem.isBlank())) {
            if (service != null && service.getNombre() != null && !service.getNombre().isBlank()) {
                nombreItem = service.getNombre().trim();
            } else if (product != null && product.getNombre() != null && !product.getNombre().isBlank()) {
                nombreItem = product.getNombre().trim();
            } else {
                nombreItem = "Item";
            }
        }

        if (costoUnitario == null) {
            costoUnitario = BigDecimal.ZERO;
        }
        if (ganancia == null) {
            ganancia = BigDecimal.ZERO;
        }
    }
}
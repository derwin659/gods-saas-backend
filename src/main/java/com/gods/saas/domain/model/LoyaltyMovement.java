package com.gods.saas.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "loyalty_movement")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoyaltyMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "movement_id")
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "loyalty_id")
    private Long loyaltyId;

    @Column(name = "tipo", nullable = false, length = 30)
    private String tipo; // EARN / REDEEM / ADJUST / EXPIRE

    @Column(name = "origen", nullable = false, length = 30)
    private String origen; // SALE / APPOINTMENT / REWARD / MANUAL

    @Column(name = "referencia_id")
    private Long referenciaId;

    @Column(name = "descripcion", length = 255)
    private String descripcion;

    @Column(name = "puntos", nullable = false)
    private Integer puntos;

    @Column(name = "saldo_resultante")
    private Integer saldoResultante;

    @Column(name = "creado_por")
    private Long creadoPor;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @PrePersist
    public void prePersist() {
        if (fechaCreacion == null) {
            fechaCreacion = LocalDateTime.now();
        }
    }
}

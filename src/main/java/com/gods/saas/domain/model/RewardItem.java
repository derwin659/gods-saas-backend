package com.gods.saas.domain.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "reward_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RewardItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reward_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    private String nombre;
    private String descripcion;

    @Column(name = "puntos_requeridos")
    private Integer puntosRequeridos;

    private Integer stock;

    @Column(name = "imagen_url")
    private String imagenUrl;

    private Boolean activo;
}

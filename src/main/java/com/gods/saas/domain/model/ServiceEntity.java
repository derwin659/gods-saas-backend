package com.gods.saas.domain.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "service")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "service_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "duracion_minutos")
    private Integer duracionMinutos;

    @Column(nullable = false)
    private Double precio;

    @Column(length = 100)
    private String categoria;

    private Boolean activo;
}

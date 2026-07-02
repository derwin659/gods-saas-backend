package com.gods.saas.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

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

    @Column(name = "precio_variable", nullable = false)
    @Builder.Default
    private Boolean precioVariable = false;

    @Column(length = 100)
    private String categoria;

    private Boolean activo;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "image_public_id", length = 255)
    private String imagePublicId;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by_user_id")
    private Long deletedByUserId;

    @Column(name = "deletion_reason", length = 500)
    private String deletionReason;
}

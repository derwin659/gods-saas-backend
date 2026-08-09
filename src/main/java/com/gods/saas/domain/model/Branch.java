package com.gods.saas.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "branch")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Branch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "branch_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(length = 200)
    private String direccion;

    @Column(length = 30)
    private String telefono;
    @Column(length = 120)
    private String ciudad;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "public_visible")
    private Boolean publicVisible;

    @Column(name = "directory_enabled")
    private Boolean directoryEnabled;

    @Column(name = "public_description", length = 500)
    private String publicDescription;

    @Builder.Default
    @Column(name = "walk_in_enabled", nullable = false)
    private Boolean walkInEnabled = false;

    @Builder.Default
    @Column(name = "walk_in_paused", nullable = false)
    private Boolean walkInPaused = false;

    @Column(name = "walk_in_estimated_wait_minutes")
    private Integer walkInEstimatedWaitMinutes;

    @Column(name = "walk_in_message", length = 200)
    private String walkInMessage;
    private Boolean activo;

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "image_public_id", length = 255)
    private String imagePublicId;

    @PrePersist
    void applyDefaults() {
        if (publicVisible == null) publicVisible = false;
        if (directoryEnabled == null) directoryEnabled = false;
        if (walkInEnabled == null) walkInEnabled = false;
        if (walkInPaused == null) walkInPaused = false;
        if (activo == null) activo = true;
        if (fechaCreacion == null) fechaCreacion = LocalDateTime.now();
    }
    public Branch(Long branchId) {
        this.id = branchId;
    }

}

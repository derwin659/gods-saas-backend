package com.gods.saas.domain.model;

import com.gods.saas.domain.enums.PromotionRedirectType;
import com.gods.saas.domain.enums.PromotionType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "promotion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Promotion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "promotion_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Column(name = "titulo", nullable = false, length = 120)
    private String titulo;

    @Column(name = "subtitulo", length = 220)
    private String subtitulo;

    @Column(name = "descripcion", columnDefinition = "text")
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30)
    private PromotionType tipo;

    @Column(name = "badge", length = 30)
    private String badge;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "icon_name", length = 60)
    private String iconName;

    @Column(name = "price_text", length = 80)
    private String priceText;

    @Column(name = "cta_label", length = 40)
    private String ctaLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "redirect_type", length = 30)
    private PromotionRedirectType redirectType;

    @Column(name = "redirect_value", length = 120)
    private String redirectValue;

    @Column(name = "destacado", nullable = false)
    private boolean destacado;

    @Column(name = "solo_clientes_con_puntos", nullable = false)
    private boolean soloClientesConPuntos;

    @Column(name = "puntos_minimos")
    private Integer puntosMinimos;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    @Column(name = "fecha_inicio")
    private LocalDateTime fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDateTime fechaFin;

    @Column(name = "orden_visual", nullable = false)
    private Integer ordenVisual;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();

        if (redirectType == null) {
            redirectType = PromotionRedirectType.NONE;
        }
        if (ordenVisual == null) {
            ordenVisual = 0;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
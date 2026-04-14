package com.gods.saas.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "subscription")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sub_id")
    private Long subId;

    @Column(name = "custom_rewards_enabled")
    private Boolean customRewardsEnabled;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "plan")
    private String plan;

    @Column(name = "precio_mensual")
    private Double precioMensual;

    @Column(name = "estado")
    private String estado;

    @Column(name = "fecha_inicio")
    private LocalDateTime fechaInicio;

    @Column(name = "fecha_renovacion")
    private LocalDateTime fechaRenovacion;

    @Column(name = "fecha_fin")
    private LocalDateTime fechaFin;

    @Column(name = "trial", nullable = false)
    private boolean trial;

    @Column(name = "dias_gracia", nullable = false)
    private Integer diasGracia;

    @Column(name = "max_branches")
    private Integer maxBranches;

    @Column(name = "max_barbers")
    private Integer maxBarbers;

    @Column(name = "max_admins")
    private Integer maxAdmins;

    @Column(name = "ai_enabled", nullable = false)
    private boolean aiEnabled;

    @Column(name = "loyalty_enabled", nullable = false)
    private boolean loyaltyEnabled;

    @Column(name = "promotions_enabled", nullable = false)
    private boolean promotionsEnabled;

    @Column(name = "billing_cycle", nullable = false)
    private String billingCycle;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "observaciones")
    private String observaciones;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;


    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (diasGracia == null) diasGracia = 0;
        if (billingCycle == null) billingCycle = "MONTHLY";
        if (currency == null) currency = "USD";

        System.out.println("SUBSCRIPTION PREPERSIST => createdAt=" + createdAt + ", updatedAt=" + updatedAt);
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
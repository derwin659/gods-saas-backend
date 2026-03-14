package com.gods.saas.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "reward_redemption")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RewardRedemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "redemption_id")
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "reward_id", nullable = false)
    private Long rewardId;

    @Column(name = "puntos_usados", nullable = false)
    private Integer puntosUsados;

    @Column(name = "estado", nullable = false, length = 30)
    private String estado; // GENERATED / USED / CANCELLED

    @Column(name = "codigo", length = 50)
    private String codigo;

    @Column(name = "notas", length = 255)
    private String notas;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_uso")
    private LocalDateTime fechaUso;

    @Column(name = "usado_en_branch_id")
    private Long usadoEnBranchId;

    @Column(name = "usado_por_user_id")
    private Long usadoPorUserId;

    @PrePersist
    public void prePersist() {
        if (fechaCreacion == null) {
            fechaCreacion = LocalDateTime.now();
        }
    }
}

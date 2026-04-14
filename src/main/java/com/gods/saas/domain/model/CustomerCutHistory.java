package com.gods.saas.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "customer_cut_history",
        indexes = {
                @Index(name = "idx_cut_history_tenant_customer_fecha", columnList = "tenant_id, customer_id, fecha_corte"),
                @Index(name = "idx_cut_history_sale", columnList = "sale_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_cut_history_sale", columnNames = {"sale_id"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerCutHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "customer_cut_history_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "barber_user_id")
    private AppUser barberUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id")
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @Column(name = "cut_name", nullable = false, length = 180)
    private String cutName;

    @Column(name = "cut_description", length = 500)
    private String cutDescription;

    @Column(name = "observations", length = 1000)
    private String observations;

    @Column(name = "cut_detail", length = 1000)
    private String cutDetail;

    @Column(name = "fecha_corte", nullable = false)
    private LocalDateTime fechaCorte;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        // no asignar fechas aquí
    }

    @PreUpdate
    void preUpdate() {
        // no asignar fechas aquí
    }
}
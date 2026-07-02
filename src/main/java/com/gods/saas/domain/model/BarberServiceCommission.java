package com.gods.saas.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "barber_service_commission", uniqueConstraints = @UniqueConstraint(
        name = "uk_barber_service_commission",
        columnNames = {"tenant_id", "branch_id", "barber_user_id", "service_id"}
))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BarberServiceCommission {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "barber_service_commission_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "barber_user_id", nullable = false)
    private AppUser barber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceEntity service;

    @Column(name = "commission_percentage", precision = 5, scale = 2, nullable = false)
    private BigDecimal commissionPercentage;
}

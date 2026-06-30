package com.gods.saas.domain.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "barber_branch_service", uniqueConstraints = @UniqueConstraint(name = "uk_barber_branch_service", columnNames = {"tenant_id","branch_id","barber_user_id","service_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BarberBranchService {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "barber_branch_service_id")
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "barber_user_id", nullable = false)
    private AppUser barber;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "service_id", nullable = false)
    private ServiceEntity service;
}

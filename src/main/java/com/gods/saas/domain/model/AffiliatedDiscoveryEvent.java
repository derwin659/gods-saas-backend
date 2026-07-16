package com.gods.saas.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "affiliated_discovery_event")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AffiliatedDiscoveryEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "affiliated_discovery_event_id")
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;
    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @PrePersist void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
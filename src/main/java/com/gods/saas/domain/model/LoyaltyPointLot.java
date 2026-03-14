package com.gods.saas.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "loyalty_point_lot")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoyaltyPointLot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "lot_id")
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loyalty_id", nullable = false)
    private LoyaltyAccount loyaltyAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movement_id")
    private LoyaltyMovement movement;

    @Column(name = "source_type", nullable = false, length = 30)
    private String sourceType;

    @Column(name = "source_reference_id")
    private Long sourceReferenceId;

    @Column(name = "points_earned", nullable = false)
    private Integer pointsEarned;

    @Column(name = "points_available", nullable = false)
    private Integer pointsAvailable;

    @Column(name = "earned_at", nullable = false)
    private LocalDateTime earnedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "status", nullable = false, length = 20)
    private String status;
}

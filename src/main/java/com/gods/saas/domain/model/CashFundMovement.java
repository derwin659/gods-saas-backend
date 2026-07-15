package com.gods.saas.domain.model;

import com.gods.saas.domain.enums.CashFundMovementType;
import com.gods.saas.domain.enums.PaymentMethod;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cash_fund_movement")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CashFundMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cash_fund_movement_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cash_register_id")
    private CashRegister cashRegister;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private AppUser actorUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 40, nullable = false)
    private CashFundMovementType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 30, nullable = false)
    private PaymentMethod paymentMethod;

    @Column(name = "amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "concept", length = 200, nullable = false)
    private String concept;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "movement_date", nullable = false)
    private LocalDateTime movementDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (movementDate == null) movementDate = now;
        if (createdAt == null) createdAt = now;
        if (amount == null) amount = BigDecimal.ZERO;
        if (paymentMethod == null) paymentMethod = PaymentMethod.CASH;
    }
}
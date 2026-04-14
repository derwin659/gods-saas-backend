package com.gods.saas.domain.model;

import com.gods.saas.domain.enums.CashMovementType;
import com.gods.saas.domain.enums.PaymentMethod;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cash_movement")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CashMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cash_movement_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cash_register_id", nullable = false)
    private CashRegister cashRegister;

    /**
     * Usuario que registró el movimiento.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    /**
     * Barbero involucrado si aplica (adelanto/pago).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "barber_user_id")
    private AppUser barberUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 30, nullable = false)
    private CashMovementType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
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
    }
}
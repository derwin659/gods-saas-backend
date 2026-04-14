package com.gods.saas.domain.model;

import com.gods.saas.domain.enums.CashRegisterStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cash_register")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CashRegister {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cash_register_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    /**
     * Usuario que abrió la caja.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "opened_by_user_id", nullable = false)
    private AppUser openedByUser;

    /**
     * Responsable de la caja del día.
     * Puede ser dueño/admin o barbero.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_user_id")
    private AppUser assignedUser;

    @Column(name = "opening_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal openingAmount;

    @Column(name = "closing_amount_expected", precision = 12, scale = 2)
    private BigDecimal closingAmountExpected;

    @Column(name = "closing_amount_counted", precision = 12, scale = 2)
    private BigDecimal closingAmountCounted;

    @Column(name = "difference_amount", precision = 12, scale = 2)
    private BigDecimal differenceAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private CashRegisterStatus status;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "opening_note", length = 500)
    private String openingNote;

    @Column(name = "closing_note", length = 500)
    private String closingNote;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (status == null) status = CashRegisterStatus.OPEN;
        if (openingAmount == null) openingAmount = BigDecimal.ZERO;
    }
}
package com.gods.saas.domain.model;

import com.gods.saas.domain.enums.BarberPaymentMode;
import com.gods.saas.domain.enums.BarberPaymentStatus;
import com.gods.saas.domain.enums.PaymentMethod;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "barber_payment",
        indexes = {
                @Index(name = "idx_barber_payment_tenant_branch", columnList = "tenant_id, branch_id"),
                @Index(name = "idx_barber_payment_barber_period", columnList = "barber_user_id, period_from, period_to"),
                @Index(name = "idx_barber_payment_cash_register", columnList = "cash_register_id"),
                @Index(name = "idx_barber_payment_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BarberPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "barber_payment_id")
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "barber_user_id", nullable = false)
    private AppUser barberUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registered_by_user_id", nullable = false)
    private AppUser registeredByUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", length = 20, nullable = false)
    private BarberPaymentMode paymentMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private BarberPaymentStatus status;

    @Column(name = "period_from", nullable = false)
    private LocalDate periodFrom;

    @Column(name = "period_to", nullable = false)
    private LocalDate periodTo;

    @Column(name = "base_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal baseAmount;

    @Column(name = "percentage_applied", precision = 5, scale = 2)
    private BigDecimal percentageApplied;

    @Column(name = "commission_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal commissionAmount;

    @Column(name = "advances_applied", precision = 12, scale = 2, nullable = false)
    private BigDecimal advancesApplied;

    @Column(name = "previous_payments_applied", precision = 12, scale = 2, nullable = false)
    private BigDecimal previousPaymentsApplied;

    @Column(name = "amount_paid", precision = 12, scale = 2, nullable = false)
    private BigDecimal amountPaid;

    @Column(name = "remaining_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal remainingAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20, nullable = false)
    private PaymentMethod paymentMethod;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cash_movement_id")
    private CashMovement cashMovement;

    @Column(name = "concept", length = 200, nullable = false)
    private String concept;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "salary_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal salaryAmount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (salaryAmount == null) salaryAmount = BigDecimal.ZERO;
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (baseAmount == null) baseAmount = BigDecimal.ZERO;
        if (commissionAmount == null) commissionAmount = BigDecimal.ZERO;
        if (advancesApplied == null) advancesApplied = BigDecimal.ZERO;
        if (previousPaymentsApplied == null) previousPaymentsApplied = BigDecimal.ZERO;
        if (amountPaid == null) amountPaid = BigDecimal.ZERO;
        if (remainingAmount == null) remainingAmount = BigDecimal.ZERO;
        if (status == null) status = BarberPaymentStatus.PAID;
    }
}
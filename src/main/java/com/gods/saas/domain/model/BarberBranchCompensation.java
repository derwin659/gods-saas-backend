package com.gods.saas.domain.model;

import com.gods.saas.domain.enums.SalaryFrequency;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "barber_branch_compensation", uniqueConstraints = @UniqueConstraint(
        name = "uk_barber_branch_compensation",
        columnNames = {"tenant_id", "branch_id", "barber_user_id"}
))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BarberBranchCompensation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "compensation_id")
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

    @Column(name = "salary_mode", nullable = false)
    private Boolean salaryMode;

    @Column(name = "commission_percentage", precision = 5, scale = 2)
    private BigDecimal commissionPercentage;

    @Enumerated(EnumType.STRING)
    @Column(name = "salary_frequency", length = 20)
    private SalaryFrequency salaryFrequency;

    @Column(name = "fixed_salary_amount", precision = 12, scale = 2)
    private BigDecimal fixedSalaryAmount;

    @Column(name = "salary_start_date")
    private LocalDate salaryStartDate;
}

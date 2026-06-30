package com.gods.saas.domain.dto;

import com.gods.saas.domain.enums.SalaryFrequency;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class BarberBranchCompensationDto {
    private Long branchId;
    private String branchName;
    private Boolean salaryMode;
    private BigDecimal commissionPercentage;
    private SalaryFrequency salaryFrequency;
    private BigDecimal fixedSalaryAmount;
    private LocalDate salaryStartDate;
}

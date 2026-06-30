package com.gods.saas.domain.dto.request;

import com.gods.saas.domain.enums.SalaryFrequency;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class SaveBarberBranchCompensationRequest {
    private Boolean salaryMode = false;
    private BigDecimal commissionPercentage;
    private SalaryFrequency salaryFrequency;
    private BigDecimal fixedSalaryAmount;
    private LocalDate salaryStartDate;
}

package com.gods.saas.domain.dto;

import com.gods.saas.domain.enums.SalaryFrequency;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class EmployeeCompensationRequest {
    private Boolean salaryEnabled;
    private BigDecimal fixedSalaryAmount;
    private SalaryFrequency salaryFrequency;
    private LocalDate salaryStartDate;
}
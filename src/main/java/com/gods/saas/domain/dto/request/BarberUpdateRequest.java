package com.gods.saas.domain.dto.request;

import com.gods.saas.domain.enums.SalaryFrequency;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class BarberUpdateRequest {

    @NotBlank
    private String nombre;

    @NotBlank
    private String apellido;

    @NotBlank
    @Email
    private String email;

    private String phone;

    @NotNull
    private Long branchId;

    @NotNull
    private Boolean activo;

    private Boolean salaryMode = false;
    private BigDecimal commissionPercentage;
    private SalaryFrequency salaryFrequency;
    private BigDecimal fixedSalaryAmount;
    private LocalDate salaryStartDate;
}
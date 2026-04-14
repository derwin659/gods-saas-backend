package com.gods.saas.domain.dto.request;

import com.gods.saas.domain.enums.SalaryFrequency;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class BarberCreateRequest {

    @NotBlank
    private String nombre;

    @NotBlank
    private String apellido;

    @NotBlank
    @Email
    private String email;

    private String phone;

    @NotBlank
    private String password;

    @NotNull
    private Long branchId;

    private Boolean activo = true;

    private Boolean salaryMode = false;
    private BigDecimal commissionPercentage;
    private SalaryFrequency salaryFrequency;
    private BigDecimal fixedSalaryAmount;
    private LocalDate salaryStartDate;
}
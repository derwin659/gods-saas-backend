package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class BarberResponse {
    private Long userId;
    private String nombre;
    private String apellido;
    private String email;
    private String phone;
    private String rol;
    private Boolean activo;
    private Long branchId;
    private String branchNombre;
    private String photoUrl;
    private Boolean salaryMode;
    private BigDecimal commissionPercentage;
    private String salaryFrequency;
    private BigDecimal fixedSalaryAmount;
    private LocalDate salaryStartDate;
}
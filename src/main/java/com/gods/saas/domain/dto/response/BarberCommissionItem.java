package com.gods.saas.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BarberCommissionItem {

    private LocalDate fecha;

    // Compatibilidad con Flutter anterior
    private BigDecimal ventas;
    private BigDecimal comision;

    // Nuevo detalle por día
    private BigDecimal baseSales;
    private BigDecimal serviceCommissionAmount;
    private BigDecimal productCommissionAmount;
    private BigDecimal tipsAmount;
    private BigDecimal grossAmount;
    private BigDecimal advancesApplied;
    private BigDecimal previousPaymentsApplied;
    private BigDecimal pendingAmount;

    // Adelantos / descuentos aplicados en este día
    private List<BarberAdvanceDetailResponse> advances;
}

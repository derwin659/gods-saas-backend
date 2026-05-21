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
public class BarberCommissionResponse {

    private String barberName;

    private LocalDate from;
    private LocalDate to;

    // Compatibilidad con Flutter anterior
    private BigDecimal totalVentas;
    private BigDecimal totalComision;
    private BigDecimal porcentajeComision;

    // Nuevo resumen para liquidación del barbero
    private BigDecimal baseSales;
    private BigDecimal serviceCommissionAmount;
    private BigDecimal productCommissionAmount;
    private BigDecimal tipsAmount;
    private BigDecimal grossAmount;
    private BigDecimal advancesApplied;
    private BigDecimal previousPaymentsApplied;
    private BigDecimal pendingAmount;

    // Detalle de adelantos / descuentos aplicados en el rango filtrado
    private List<BarberAdvanceDetailResponse> advances;

    private List<BarberCommissionItem> items;
}

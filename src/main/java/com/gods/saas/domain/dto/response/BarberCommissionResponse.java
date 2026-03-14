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

    private BigDecimal totalVentas;
    private BigDecimal totalComision;

    private BigDecimal porcentajeComision;

    private List<BarberCommissionItem> items;

}
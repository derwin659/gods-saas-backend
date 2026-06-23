package com.gods.saas.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@AllArgsConstructor
@Builder
public class BarberHomeResponse {
    private String tenantName;
    private String barberName;
    private String barberPhotoUrl;
    private Boolean canSell;
    private String currency;
    private String currencySymbol;
    private int citasHoy;
    private int atendidosHoy;
    private BigDecimal ventasHoy;
    private List<BarberHomeAppointmentResponse> proximasCitas;
    private BarberQuickStatsResponse stats;
    private CommissionSummaryResponse commissions;
}

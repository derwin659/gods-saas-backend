package com.gods.saas.domain.dto.response;


import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class BarberQuickStatsResponse {
    private int serviciosHoy;
    private BigDecimal propinas;
    private int iaUsados;
    private int cancelaciones;
}

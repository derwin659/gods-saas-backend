package com.gods.saas.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerCustomerHistoryResponse {
    private Long id;
    private String fecha;
    private String servicio;
    private String barbero;
    private String barberPhotoUrl;
    private Integer puntos;
    private BigDecimal total;
    private String tipo;
}

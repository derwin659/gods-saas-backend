package com.gods.saas.domain.dto.response;


import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class SaleResponse {
    private Long saleId;
    private Long tenantId;
    private Long branchId;
    private Long customerId;
    private Long userId;
    private Long appointmentId;
    private String metodoPago;
    private Double total;
    private LocalDateTime fechaCreacion;
    private Integer puntosGanados;
    private Integer puntosDisponibles;
    private List<SaleItemResponse> items;
}

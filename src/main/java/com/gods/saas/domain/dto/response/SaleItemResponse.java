package com.gods.saas.domain.dto.response;


import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SaleItemResponse {
    private Long id;
    private Long serviceId;
    private String serviceNombre;
    private Long productId;
    private String productNombre;
    private Integer cantidad;
    private Double precioUnitario;
    private Double subtotal;
}

package com.gods.saas.domain.dto.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateSaleItemRequest {

    private Long serviceId;
    private Long productId;

    /**
     * Barbero que realizó este item.
     */
    private Long barberUserId;

    private Integer cantidad;

    /**
     * Si viene null, se toma del precio del servicio o producto.
     */
    private BigDecimal precioUnitario;
}
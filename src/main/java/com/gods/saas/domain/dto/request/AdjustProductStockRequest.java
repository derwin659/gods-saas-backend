package com.gods.saas.domain.dto.request;

import lombok.Data;

@Data
public class AdjustProductStockRequest {

    /**
     * Puede ser positivo o negativo.
     * Ejemplo: +10 entrada, -2 ajuste por pérdida.
     */
    private Integer quantityDelta;

    private String observacion;
}

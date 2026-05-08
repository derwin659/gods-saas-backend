package com.gods.saas.domain.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class AdjustProductStockRequest {

    /**
     * Puede ser positivo o negativo.
     * Ejemplo: +10 entrada, -2 ajuste por pérdida.
     */
    private Integer quantityDelta;

    /**
     * Valores recomendados:
     * ENTRADA, AJUSTE, PERDIDA, DEVOLUCION, SALIDA_INTERNA.
     * VENTA se usa normalmente desde el flujo de venta, no desde ajuste manual.
     */
    private String tipoMovimiento;

    /**
     * Datos opcionales para recepción/compra de mercadería.
     */
    private String proveedor;
    private LocalDate fechaRecepcion;
    private BigDecimal costoUnitario;
    private String numeroComprobante;

    private String observacion;
}

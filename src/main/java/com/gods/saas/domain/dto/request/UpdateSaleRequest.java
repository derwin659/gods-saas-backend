package com.gods.saas.domain.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class UpdateSaleRequest {
    private Long customerId;
    private String metodoPago;
    private BigDecimal subtotal;
    private BigDecimal discount;
    private BigDecimal total;
    private BigDecimal cashReceived;
    private BigDecimal changeAmount;

    /**
     * Opcional: permite corregir barbero y valor de los items existentes.
     * No recrea productos ni servicios para evitar movimientos de inventario inesperados.
     */
    private List<UpdateSaleItemRequest> items;

    /**
     * Opcional para compatibilidad hacia atrás.
     * Si viene null, se mantiene el comportamiento anterior y no se reemplazan pagos.
     * Si viene con datos, reemplaza los métodos de pago de la venta.
     */
    private List<SalePaymentRequest> payments;

    @Data
    public static class UpdateSaleItemRequest {
        private Long saleItemId;
        private Long id;
        private Long barberUserId;
        private BigDecimal precioUnitario;

        public Long resolvedItemId() {
            return saleItemId != null ? saleItemId : id;
        }
    }
}

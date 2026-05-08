package com.gods.saas.domain.dto.response;

import com.gods.saas.domain.model.AppUser;
import com.gods.saas.domain.model.StockMovement;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class StockMovementResponse {
    private Long id;
    private Long productId;
    private String productName;
    private Long branchId;
    private String branchName;
    private Long userId;
    private String userName;

    private String tipoMovimiento;
    private Integer cantidad;
    private Integer stockAnterior;
    private Integer stockNuevo;

    private BigDecimal costoUnitario;
    private BigDecimal precioUnitario;
    private BigDecimal costoTotal;

    private String proveedor;
    private LocalDate fechaRecepcion;
    private String numeroComprobante;
    private String observacion;
    private LocalDateTime fechaCreacion;

    public static StockMovementResponse fromEntity(StockMovement movement) {
        BigDecimal costoUnitario = movement.getCostoUnitario() == null
                ? BigDecimal.ZERO
                : movement.getCostoUnitario();
        Integer cantidad = movement.getCantidad() == null ? 0 : movement.getCantidad();

        return StockMovementResponse.builder()
                .id(movement.getId())
                .productId(movement.getProduct() != null ? movement.getProduct().getId() : null)
                .productName(movement.getProduct() != null ? movement.getProduct().getNombre() : null)
                .branchId(movement.getBranch() != null ? movement.getBranch().getId() : null)
                .branchName(null)
                .userId(movement.getUser() != null ? movement.getUser().getId() : null)
                .userName(resolveUserName(movement.getUser()))
                .tipoMovimiento(movement.getTipoMovimiento())
                .cantidad(cantidad)
                .stockAnterior(movement.getStockAnterior())
                .stockNuevo(movement.getStockNuevo())
                .costoUnitario(costoUnitario)
                .precioUnitario(movement.getPrecioUnitario())
                .costoTotal(costoUnitario.multiply(BigDecimal.valueOf(cantidad)))
                .proveedor(movement.getProveedor())
                .fechaRecepcion(movement.getFechaRecepcion())
                .numeroComprobante(movement.getNumeroComprobante())
                .observacion(movement.getObservacion())
                .fechaCreacion(movement.getFechaCreacion())
                .build();
    }

    private static String resolveUserName(AppUser user) {
        if (user == null) return null;

        String nombre = user.getNombre() == null ? "" : user.getNombre().trim();
        String apellido = user.getApellido() == null ? "" : user.getApellido().trim();
        String full = (nombre + " " + apellido).trim();

        if (!full.isBlank()) return full;
        if (user.getEmail() != null && !user.getEmail().isBlank()) return user.getEmail();
        return "Usuario " + user.getId();
    }
}

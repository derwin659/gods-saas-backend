package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class SaleItemResponse {
    private Long id;
    private String serviceNombre;
    private Long saleItemId;
    private Long serviceId;
    private String serviceName;
    private Long productId;
    private String productName;
    private Long barberUserId;
    private String barberUserName;
    private Integer cantidad;
    private BigDecimal precioUnitario;
    private BigDecimal subtotal;
    private BigDecimal productCommissionAmount;
}

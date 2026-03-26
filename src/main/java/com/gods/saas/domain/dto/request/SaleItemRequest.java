package com.gods.saas.domain.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaleItemRequest {
    private Long serviceId;
    private Long productId;
    private Long barberUserId;
    private Integer cantidad;
    private Double precioUnitario;
}

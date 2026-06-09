package com.gods.saas.domain.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateLocalConsumptionOrderRequest {
    private Long branchId;
    private String notes;
    private List<Item> items;

    @Data
    public static class Item {
        private Long serviceId;
        private Long productId;
        private Long barberUserId;
        private Integer quantity;
        private BigDecimal unitPrice;
        private String notes;
    }
}

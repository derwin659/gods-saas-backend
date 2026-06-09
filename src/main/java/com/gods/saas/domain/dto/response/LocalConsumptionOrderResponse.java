package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class LocalConsumptionOrderResponse {
    private Long id;
    private Long tenantId;
    private Long branchId;
    private String branchName;
    private Long customerId;
    private String customerName;
    private String customerPhone;
    private String status;
    private String statusLabel;
    private BigDecimal total;
    private String notes;
    private String adminNote;
    private String createdAt;
    private String updatedAt;
    private String handledAt;
    private Long saleId;
    private List<Item> items;

    @Data
    @Builder
    public static class Item {
        private Long id;
        private String type;
        private Long serviceId;
        private String serviceName;
        private Long productId;
        private String productName;
        private Long barberUserId;
        private String barberUserName;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal subtotal;
        private String notes;
    }
}

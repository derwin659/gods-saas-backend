package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class BarberSaleReviewResponse {
    private long pendingCount;
    private long approvedCount;
    private long rejectedCount;
    private List<Item> items;

    @Data
    @Builder
    public static class Item {
        private Long saleId;
        private String status;
        private String customerName;
        private String branchName;
        private List<String> services;
        private BigDecimal total;
        private LocalDateTime registeredAt;
        private LocalDateTime reviewedAt;
        private String reviewedBy;
        private String rejectionReason;
    }
}

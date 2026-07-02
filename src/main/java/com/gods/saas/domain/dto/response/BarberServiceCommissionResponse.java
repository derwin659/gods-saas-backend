package com.gods.saas.domain.dto.response;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BarberServiceCommissionResponse {
    private Long barberId;
    private Long branchId;
    private BigDecimal defaultPercentage;
    private List<Item> services;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Item {
        private Long serviceId;
        private String serviceName;
        private String imageUrl;
        private BigDecimal percentage;
        private boolean custom;
    }
}

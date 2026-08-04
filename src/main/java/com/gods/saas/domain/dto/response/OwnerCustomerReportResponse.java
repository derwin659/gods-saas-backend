package com.gods.saas.domain.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record OwnerCustomerReportResponse(
        String from,
        String to,
        String previousFrom,
        String previousTo,
        Summary summary,
        List<Item> items
) {
    public record Summary(
            Integer totalRegistered,
            Integer previousRegistered,
            Double registeredVariationPercent,
            Integer totalFiltered,
            Integer activeCustomers,
            Integer inactiveCustomers,
            Integer vipCustomers,
            Integer frequentCustomers,
            Integer newCustomers,
            Integer withMarketingWhatsapp,
            Integer optedOutWhatsapp,
            BigDecimal totalSpent,
            BigDecimal averageSpent,
            Map<String, Integer> loyaltyTierCounts
    ) {}

    public record Item(
            Long customerId,
            String fullName,
            String phone,
            String email,
            String registeredAt,
            String lastVisit,
            Long branchId,
            String branchName,
            Long visits,
            BigDecimal totalSpent,
            Integer points,
            String status,
            String loyaltyTierName,
            String loyaltyTierColor,
            Boolean whatsappTransactionalEnabled,
            Boolean whatsappMarketingEnabled,
            Boolean whatsappOptedOut
    ) {}
}
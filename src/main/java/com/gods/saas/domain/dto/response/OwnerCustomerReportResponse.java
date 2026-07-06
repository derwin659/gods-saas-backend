package com.gods.saas.domain.dto.response;

import java.math.BigDecimal;
import java.util.List;

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
            BigDecimal averageSpent
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
            Boolean whatsappTransactionalEnabled,
            Boolean whatsappMarketingEnabled,
            Boolean whatsappOptedOut
    ) {}
}

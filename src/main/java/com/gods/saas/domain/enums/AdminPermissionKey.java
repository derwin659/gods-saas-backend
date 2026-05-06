package com.gods.saas.domain.enums;

import java.util.Arrays;
import java.util.List;

public enum AdminPermissionKey {

    CONFIG_ACCESS,
    CONFIG_BARBERS,
    CONFIG_SERVICES,
    CONFIG_PRODUCTS,
    CONFIG_BRANCHES,
    CONFIG_PAYMENT_METHODS,
    CONFIG_REWARDS,
    CONFIG_PROMOTIONS,

    CASH_ACCESS,
    CASH_REGISTER_INCOME,
    CASH_REGISTER_EXPENSE,
    CASH_CLOSE,

    REPORTS_ACCESS,
    REPORTS_PROFITABILITY,
    REPORTS_BARBER_PAYMENTS,

    AGENDA_ACCESS,
    CUSTOMERS_ACCESS;

    public static boolean isValid(String key) {
        if (key == null || key.isBlank()) return false;
        return Arrays.stream(values())
                .anyMatch(p -> p.name().equalsIgnoreCase(key.trim()));
    }

    public static List<String> defaultsForNewAdmin() {
        return List.of(
                CASH_ACCESS,
                AGENDA_ACCESS,
                CUSTOMERS_ACCESS
        ).stream().map(Enum::name).toList();
    }
}
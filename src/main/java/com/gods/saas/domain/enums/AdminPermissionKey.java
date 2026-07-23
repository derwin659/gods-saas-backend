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
    CONFIG_SCHEDULES,

    CASH_ACCESS,
    CASH_OPEN,
    CASH_REGISTER_INCOME,
    CASH_REGISTER_EXPENSE,
    CASH_FUND_MANAGE,
    CASH_CLOSE,
    CASH_DELETE_SALES,
    CASH_DELETE_MOVEMENTS,
    CASH_EDIT_PAST_SALES,
    CASH_APPROVE_SALES,
    CASH_PRINT_RECEIPT,
    CASH_OPEN_DRAWER,

    REPORTS_ACCESS,
    REPORTS_PROFITABILITY,
    REPORTS_BARBER_PAYMENTS,

    AGENDA_ACCESS,
    CUSTOMERS_ACCESS,
    CUSTOMERS_VIEW_PHONE;

    public static boolean isValid(String key) {
        if (key == null || key.isBlank()) return false;
        return Arrays.stream(values())
                .anyMatch(p -> p.name().equalsIgnoreCase(key.trim()));
    }

    public static List<String> defaultsForNewCashier() {
        return List.of(
                CASH_ACCESS,
                CASH_REGISTER_INCOME,
                CASH_REGISTER_EXPENSE,
                CASH_APPROVE_SALES,
                CASH_PRINT_RECEIPT,
                CASH_OPEN_DRAWER,
                CUSTOMERS_ACCESS
        ).stream().map(Enum::name).toList();
    }

    public static List<String> defaultsForNewAdmin() {
        return List.of(
                CASH_ACCESS,
                CASH_APPROVE_SALES,
                CASH_PRINT_RECEIPT,
                CASH_OPEN_DRAWER,
                AGENDA_ACCESS,
                CUSTOMERS_ACCESS
        ).stream().map(Enum::name).toList();
    }
}

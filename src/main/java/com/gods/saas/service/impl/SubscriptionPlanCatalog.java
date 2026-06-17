package com.gods.saas.service.impl;

import com.gods.saas.domain.model.Subscription;
import com.gods.saas.exception.BusinessException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

public final class SubscriptionPlanCatalog {

    public static final String FREE = "FREE";
    public static final String BASIC = "BASIC";
    public static final String STARTER = "STARTER";
    public static final String GROWTH = "GROWTH";
    public static final String PRO = "PRO";
    public static final String ENTERPRISE = "ENTERPRISE";
    public static final String STARTER_LEGACY = "STARTER_LEGACY";
    public static final String PRO_LEGACY = "PRO_LEGACY";
    public static final String GODS_AI = "GODS_AI";

    public static final List<String> PUBLIC_PLAN_CODES = List.of(
            FREE,
            BASIC,
            STARTER,
            GROWTH,
            PRO,
            ENTERPRISE
    );

    public static final List<String> ALL_PLAN_CODES = List.of(
            FREE,
            BASIC,
            STARTER,
            GROWTH,
            PRO,
            ENTERPRISE,
            STARTER_LEGACY,
            PRO_LEGACY,
            GODS_AI
    );

    private SubscriptionPlanCatalog() {
    }

    public static String normalize(String plan) {
        String value = plan == null ? "" : plan.trim().toUpperCase(Locale.ROOT).replace("-", "_");
        return switch (value) {
            case "", "NORMAL", "STANDARD" -> STARTER;
            case "SOLO", "INDEPENDENT" -> BASIC;
            case "STARTER LEGACY" -> STARTER_LEGACY;
            case "PRO LEGACY" -> PRO_LEGACY;
            case FREE, BASIC, STARTER, GROWTH, PRO, ENTERPRISE, STARTER_LEGACY, PRO_LEGACY, GODS_AI -> value;
            default -> throw new BusinessException(
                    "PLAN_INVALID",
                    "Plan no valido. Usa FREE, BASIC, STARTER, GROWTH, PRO, ENTERPRISE, STARTER_LEGACY o PRO_LEGACY"
            );
        };
    }

    public static String publicPlan(String plan) {
        return switch (normalize(plan)) {
            case STARTER_LEGACY -> STARTER;
            case PRO_LEGACY -> PRO;
            case GODS_AI -> GROWTH;
            default -> normalize(plan);
        };
    }

    public static boolean isLegacy(String plan) {
        String normalized = normalize(plan);
        return STARTER_LEGACY.equals(normalized) || PRO_LEGACY.equals(normalized);
    }

    public static BigDecimal fallbackMonthlyPrice(String plan, String currency) {
        String p = publicPlan(plan);
        String c = currency == null ? "" : currency.trim().toUpperCase(Locale.ROOT);

        return switch (c) {
            case "USD", "VES" -> switch (p) {
                case FREE -> BigDecimal.ZERO;
                case BASIC -> BigDecimal.valueOf(12);
                case GROWTH -> BigDecimal.valueOf(42);
                case PRO -> BigDecimal.valueOf(68);
                case ENTERPRISE -> BigDecimal.valueOf(118);
                default -> BigDecimal.valueOf(24);
            };
            case "EUR" -> switch (p) {
                case FREE -> BigDecimal.ZERO;
                case BASIC -> BigDecimal.valueOf(11);
                case GROWTH -> BigDecimal.valueOf(38);
                case PRO -> BigDecimal.valueOf(61);
                case ENTERPRISE -> BigDecimal.valueOf(106);
                default -> BigDecimal.valueOf(22);
            };
            case "PEN" -> switch (p) {
                case FREE -> BigDecimal.ZERO;
                case BASIC -> BigDecimal.valueOf(39.90);
                case GROWTH -> BigDecimal.valueOf(139.90);
                case PRO -> BigDecimal.valueOf(229.90);
                case ENTERPRISE -> BigDecimal.valueOf(399.90);
                default -> BigDecimal.valueOf(79.90);
            };
            default -> switch (p) {
                case FREE -> BigDecimal.ZERO;
                case BASIC -> BigDecimal.valueOf(39.90);
                case GROWTH -> BigDecimal.valueOf(139.90);
                case PRO -> BigDecimal.valueOf(229.90);
                case ENTERPRISE -> BigDecimal.valueOf(399.90);
                default -> BigDecimal.valueOf(79.90);
            };
        };
    }

    public static void applyTo(Subscription subscription, String plan, double monthlyPrice) {
        String normalized = normalize(plan);

        subscription.setPlan(normalized);
        subscription.setPrecioMensual(monthlyPrice);

        switch (normalized) {
            case FREE -> {
                subscription.setMaxBranches(1);
                subscription.setMaxBarbers(1);
                subscription.setMaxAdmins(0);
                subscription.setAiEnabled(false);
                subscription.setLoyaltyEnabled(false);
                subscription.setPromotionsEnabled(false);
                subscription.setCustomRewardsEnabled(false);
            }
            case BASIC -> {
                subscription.setMaxBranches(1);
                subscription.setMaxBarbers(1);
                subscription.setMaxAdmins(0);
                subscription.setAiEnabled(false);
                subscription.setLoyaltyEnabled(false);
                subscription.setPromotionsEnabled(false);
                subscription.setCustomRewardsEnabled(false);
            }
            case STARTER, STARTER_LEGACY -> {
                subscription.setMaxBranches(1);
                subscription.setMaxBarbers(5);
                subscription.setMaxAdmins(1);
                subscription.setAiEnabled(false);
                subscription.setLoyaltyEnabled(true);
                subscription.setPromotionsEnabled(false);
                subscription.setCustomRewardsEnabled(true);
            }
            case GROWTH, GODS_AI -> {
                subscription.setMaxBranches(2);
                subscription.setMaxBarbers(10);
                subscription.setMaxAdmins(3);
                subscription.setAiEnabled(true);
                subscription.setLoyaltyEnabled(true);
                subscription.setPromotionsEnabled(true);
                subscription.setCustomRewardsEnabled(true);
            }
            case PRO, PRO_LEGACY -> {
                subscription.setMaxBranches(3);
                subscription.setMaxBarbers(18);
                subscription.setMaxAdmins(6);
                subscription.setAiEnabled(true);
                subscription.setLoyaltyEnabled(true);
                subscription.setPromotionsEnabled(true);
                subscription.setCustomRewardsEnabled(true);
            }
            case ENTERPRISE -> {
                subscription.setMaxBranches(null);
                subscription.setMaxBarbers(null);
                subscription.setMaxAdmins(null);
                subscription.setAiEnabled(true);
                subscription.setLoyaltyEnabled(true);
                subscription.setPromotionsEnabled(true);
                subscription.setCustomRewardsEnabled(true);
            }
            default -> throw new BusinessException("PLAN_INVALID", "Plan no valido");
        }
    }
}

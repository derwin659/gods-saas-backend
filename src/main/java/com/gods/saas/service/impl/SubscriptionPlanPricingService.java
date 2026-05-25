package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.response.SubscriptionPlanPriceResponse;
import com.gods.saas.domain.model.Tenant;
import com.gods.saas.domain.model.TenantSettings;
import com.gods.saas.domain.repository.TenantRepository;
import com.gods.saas.domain.repository.TenantSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SubscriptionPlanPricingService {

    private static final List<String> PLAN_CODES = List.of("STARTER", "PRO", "GODS_AI");

    private final JdbcTemplate jdbcTemplate;
    private final TenantRepository tenantRepository;
    private final TenantSettingsRepository tenantSettingsRepository;

    public List<SubscriptionPlanPriceResponse> listMonthlyPricesForTenant(Long tenantId) {
        Tenant tenant = tenantId == null ? null : tenantRepository.findById(tenantId).orElse(null);
        String countryCode = countryCodeFor(tenant != null ? tenant.getPais() : null);
        String currency = resolveTenantCurrency(tenantId, tenant, null);

        return PLAN_CODES.stream()
                .map(plan -> {
                    BigDecimal amount = resolveMonthlyPrice(plan, countryCode, currency);
                    return SubscriptionPlanPriceResponse.builder()
                            .plan(plan)
                            .countryCode(countryCode)
                            .currency(currencyForCountry(countryCode, currency))
                            .monthlyAmount(amount.doubleValue())
                            .build();
                })
                .toList();
    }

    public BigDecimal resolveMonthlyPriceForTenant(Long tenantId, String plan, String preferredCurrency) {
        Tenant tenant = tenantId == null ? null : tenantRepository.findById(tenantId).orElse(null);
        String countryCode = countryCodeFor(tenant != null ? tenant.getPais() : null);
        String currency = resolveTenantCurrency(tenantId, tenant, preferredCurrency);
        return resolveMonthlyPrice(plan, countryCode, currency);
    }
    public List<SubscriptionPlanPriceResponse> listMonthlyPricesForCountry(String countryOrCode) {
        String countryCode = countryCodeFor(countryOrCode);
        String currency = currencyForCountry(countryCode, null);

        return PLAN_CODES.stream()
                .map(plan -> {
                    BigDecimal amount = resolveMonthlyPrice(plan, countryCode, currency);
                    return SubscriptionPlanPriceResponse.builder()
                            .plan(plan)
                            .countryCode(countryCode)
                            .currency(currency)
                            .monthlyAmount(amount.doubleValue())
                            .build();
                })
                .toList();
    }

    public BigDecimal resolveMonthlyPrice(String plan, String countryOrCode, String preferredCurrency) {
        String planCode = normalizePlan(plan);
        String countryCode = countryCodeFor(countryOrCode);
        String currency = currencyForCountry(countryCode, preferredCurrency);

        BigDecimal configured = findConfiguredMonthlyPrice(planCode, countryCode);
        if (configured != null) return configured;

        BigDecimal defaultConfigured = findConfiguredMonthlyPrice(planCode, "DEFAULT");
        if (defaultConfigured != null) return defaultConfigured;

        return fallbackMonthlyPrice(planCode, currency);
    }

    public String resolveTenantCurrency(Long tenantId, Tenant tenant, String preferredCurrency) {
        String explicit = cleanUpper(preferredCurrency);
        if (!explicit.isBlank()) return explicit;

        if (tenantId != null) {
            String settingsCurrency = tenantSettingsRepository.findByTenant_Id(tenantId)
                    .map(TenantSettings::getCurrency)
                    .map(this::cleanUpper)
                    .filter(value -> !value.isBlank())
                    .orElse("");
            if (!settingsCurrency.isBlank()) return settingsCurrency;
        }

        String countryCode = countryCodeFor(tenant != null ? tenant.getPais() : null);
        return currencyForCountry(countryCode, "PEN");
    }

    public double expectedAmount(String plan, String billingCycle, Long tenantId, String preferredCurrency) {
        BigDecimal monthly = resolveMonthlyPriceForTenant(tenantId, plan, preferredCurrency);
        BigDecimal months = switch (cleanUpper(billingCycle)) {
            case "SEMIANNUAL" -> BigDecimal.valueOf(6);
            case "ANNUAL", "YEARLY" -> BigDecimal.valueOf(12);
            default -> BigDecimal.ONE;
        };
        BigDecimal discount = switch (cleanUpper(billingCycle)) {
            case "SEMIANNUAL" -> BigDecimal.valueOf(0.90);
            case "ANNUAL", "YEARLY" -> BigDecimal.valueOf(0.80);
            default -> BigDecimal.ONE;
        };
        return monthly.multiply(months).multiply(discount).setScale(2, java.math.RoundingMode.HALF_UP).doubleValue();
    }

    private BigDecimal findConfiguredMonthlyPrice(String planCode, String countryCode) {
        try {
            List<BigDecimal> rows = jdbcTemplate.query(
                    "select amount from subscription_plan_price " +
                            "where active = true and upper(plan_code) = ? and upper(country_code) = ? " +
                            "and upper(billing_cycle) = 'MONTHLY' order by id desc limit 1",
                    (rs, rowNum) -> rs.getBigDecimal("amount"),
                    planCode,
                    countryCode
            );
            return rows.isEmpty() ? null : rows.get(0);
        } catch (DataAccessException ignored) {
            return null;
        }
    }

    private BigDecimal fallbackMonthlyPrice(String planCode, String currency) {
        String c = cleanUpper(currency);
        return switch (c) {
            case "USD", "VES" -> switch (planCode) {
                case "PRO" -> BigDecimal.valueOf(39);
                case "GODS_AI" -> BigDecimal.valueOf(79);
                default -> BigDecimal.valueOf(19);
            };
            case "EUR" -> switch (planCode) {
                case "PRO" -> BigDecimal.valueOf(35);
                case "GODS_AI" -> BigDecimal.valueOf(69);
                default -> BigDecimal.valueOf(17);
            };
            case "COP" -> switch (planCode) {
                case "PRO" -> BigDecimal.valueOf(159000);
                case "GODS_AI" -> BigDecimal.valueOf(319000);
                default -> BigDecimal.valueOf(79000);
            };
            case "MXN" -> switch (planCode) {
                case "PRO" -> BigDecimal.valueOf(759);
                case "GODS_AI" -> BigDecimal.valueOf(1529);
                default -> BigDecimal.valueOf(369);
            };
            case "CLP" -> switch (planCode) {
                case "PRO" -> BigDecimal.valueOf(38900);
                case "GODS_AI" -> BigDecimal.valueOf(78900);
                default -> BigDecimal.valueOf(18900);
            };
            case "ARS" -> switch (planCode) {
                case "PRO" -> BigDecimal.valueOf(44900);
                case "GODS_AI" -> BigDecimal.valueOf(89900);
                default -> BigDecimal.valueOf(22000);
            };
            case "BOB" -> switch (planCode) {
                case "PRO" -> BigDecimal.valueOf(269);
                case "GODS_AI" -> BigDecimal.valueOf(549);
                default -> BigDecimal.valueOf(129);
            };
            case "BRL" -> switch (planCode) {
                case "PRO" -> BigDecimal.valueOf(199);
                case "GODS_AI" -> BigDecimal.valueOf(399);
                default -> BigDecimal.valueOf(99);
            };
            case "UYU" -> switch (planCode) {
                case "PRO" -> BigDecimal.valueOf(1559);
                case "GODS_AI" -> BigDecimal.valueOf(3159);
                default -> BigDecimal.valueOf(759);
            };
            case "PYG" -> switch (planCode) {
                case "PRO" -> BigDecimal.valueOf(299000);
                case "GODS_AI" -> BigDecimal.valueOf(599000);
                default -> BigDecimal.valueOf(145000);
            };
            case "CRC" -> switch (planCode) {
                case "PRO" -> BigDecimal.valueOf(19900);
                case "GODS_AI" -> BigDecimal.valueOf(39900);
                default -> BigDecimal.valueOf(9900);
            };
            case "DOP" -> switch (planCode) {
                case "PRO" -> BigDecimal.valueOf(2299);
                case "GODS_AI" -> BigDecimal.valueOf(4599);
                default -> BigDecimal.valueOf(1099);
            };
            case "GTQ" -> switch (planCode) {
                case "PRO" -> BigDecimal.valueOf(299);
                case "GODS_AI" -> BigDecimal.valueOf(609);
                default -> BigDecimal.valueOf(149);
            };
            default -> switch (planCode) {
                case "PRO" -> BigDecimal.valueOf(79);
                case "GODS_AI" -> BigDecimal.valueOf(149);
                default -> BigDecimal.valueOf(39);
            };
        };
    }

    public String countryCodeFor(String value) {
        String normalized = normalizeCountry(value);
        return switch (normalized) {
            case "PERU", "PE" -> "PE";
            case "ESTADOSUNIDOS", "UNITEDSTATES", "USA", "US" -> "US";
            case "COLOMBIA", "CO" -> "CO";
            case "MEXICO", "MX" -> "MX";
            case "CHILE", "CL" -> "CL";
            case "ARGENTINA", "AR" -> "AR";
            case "BOLIVIA", "BO" -> "BO";
            case "BRASIL", "BRAZIL", "BR" -> "BR";
            case "VENEZUELA", "VE" -> "VE";
            case "URUGUAY", "UY" -> "UY";
            case "PARAGUAY", "PY" -> "PY";
            case "COSTARICA", "CR" -> "CR";
            case "REPUBLICADOMINICANA", "DOMINICANREPUBLIC", "DO" -> "DO";
            case "GUATEMALA", "GT" -> "GT";
            case "ESPANA", "SPAIN", "EUROPA", "EUROPE", "EU" -> "EU";
            default -> "PE";
        };
    }

    public String currencyForCountry(String countryCode, String preferredCurrency) {
        String explicit = cleanUpper(preferredCurrency);
        if (!explicit.isBlank()) return explicit;

        return switch (cleanUpper(countryCode)) {
            case "US" -> "USD";
            case "CO" -> "COP";
            case "MX" -> "MXN";
            case "CL" -> "CLP";
            case "AR" -> "ARS";
            case "BO" -> "USD";
            case "BR" -> "BRL";
            case "VE" -> "VES";
            case "UY", "PY", "CR", "DO", "GT" -> "USD";
            case "EU" -> "EUR";
            default -> "PEN";
        };
    }

    private String normalizePlan(String plan) {
        String value = cleanUpper(plan);
        return switch (value) {
            case "PRO", "GODS_AI" -> value;
            default -> "STARTER";
        };
    }

    private String cleanUpper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeCountry(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z]", "")
                .toUpperCase(Locale.ROOT);
    }
}

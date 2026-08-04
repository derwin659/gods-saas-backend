package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.UpdateLoyaltySettingsRequest;
import com.gods.saas.domain.dto.response.LoyaltySettingsResponse;
import com.gods.saas.domain.dto.response.LoyaltyTierConfig;
import com.gods.saas.domain.model.TenantSettings;
import com.gods.saas.domain.repository.TenantSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OwnerLoyaltySettingsService {

    private static final BigDecimal DEFAULT_POINTS_PER_CURRENCY_UNIT = BigDecimal.valueOf(5);
    private static final String POINTS_PER_CURRENCY_UNIT_KEY = "loyaltyPointsPerCurrencyUnit";
    public static final String WELCOME_BONUS_ENABLED_KEY = "loyaltyWelcomeBonusEnabled";
    public static final String WELCOME_BONUS_POINTS_KEY = "loyaltyWelcomeBonusPoints";
    public static final String ACTIVATION_BONUS_ENABLED_KEY = "loyaltyActivationBonusEnabled";
    public static final String ACTIVATION_BONUS_POINTS_KEY = "loyaltyActivationBonusPoints";
    public static final String TIERS_KEY = "loyaltyTiers";

    private static final int DEFAULT_WELCOME_BONUS = 100;
    private static final int DEFAULT_ACTIVATION_BONUS = 50;
    private static final int MAX_BONUS_POINTS = 100_000;
    private static final int MAX_TIERS = 20;

    private final TenantSettingsRepository tenantSettingsRepository;

    @Transactional(readOnly = true)
    public LoyaltySettingsResponse getSettings(Long tenantId) {
        TenantSettings settings = resolveSettings(tenantId);
        String currency = normalizeCurrency(settings.getCurrency(), "PEN");

        return LoyaltySettingsResponse.builder()
                .pointsPerCurrencyUnit(resolvePointsPerCurrencyUnit(settings))
                .currency(currency)
                .currencySymbol(resolveCurrencySymbol(currency))
                .welcomeBonusEnabled(readBoolean(settings, WELCOME_BONUS_ENABLED_KEY, true))
                .welcomeBonusPoints(readInt(settings, WELCOME_BONUS_POINTS_KEY, DEFAULT_WELCOME_BONUS))
                .activationBonusEnabled(readBoolean(settings, ACTIVATION_BONUS_ENABLED_KEY, true))
                .activationBonusPoints(readInt(settings, ACTIVATION_BONUS_POINTS_KEY, DEFAULT_ACTIVATION_BONUS))
                .tiers(resolveTiers(settings))
                .build();
    }

    @Transactional
    public LoyaltySettingsResponse updateSettings(Long tenantId, UpdateLoyaltySettingsRequest request) {
        if (request == null) {
            throw new RuntimeException("Ingresa la configuración de fidelización.");
        }

        TenantSettings settings = resolveSettings(tenantId);
        Map<String, Object> config = settings.getScheduleConfig() == null
                ? new HashMap<>()
                : new HashMap<>(settings.getScheduleConfig());

        if (request.getPointsPerCurrencyUnit() != null) {
            BigDecimal points = request.getPointsPerCurrencyUnit().setScale(2, RoundingMode.HALF_UP);
            if (points.compareTo(BigDecimal.ZERO) < 0 || points.compareTo(BigDecimal.valueOf(1000)) > 0) {
                throw new RuntimeException("Los puntos por unidad deben estar entre 0 y 1000.");
            }
            config.put(POINTS_PER_CURRENCY_UNIT_KEY, points);
        }

        if (request.getCurrency() != null && !request.getCurrency().trim().isEmpty()) {
            settings.setCurrency(normalizeCurrency(request.getCurrency(), "PEN"));
        }

        if (request.getWelcomeBonusEnabled() != null) {
            config.put(WELCOME_BONUS_ENABLED_KEY, request.getWelcomeBonusEnabled());
        }
        if (request.getWelcomeBonusPoints() != null) {
            config.put(WELCOME_BONUS_POINTS_KEY, validateBonus(request.getWelcomeBonusPoints(), "bienvenida"));
        }
        if (request.getActivationBonusEnabled() != null) {
            config.put(ACTIVATION_BONUS_ENABLED_KEY, request.getActivationBonusEnabled());
        }
        if (request.getActivationBonusPoints() != null) {
            config.put(ACTIVATION_BONUS_POINTS_KEY, validateBonus(request.getActivationBonusPoints(), "activación"));
        }
        if (request.getTiers() != null) {
            config.put(TIERS_KEY, serializeTiers(validateTiers(request.getTiers())));
        }

        settings.setScheduleConfig(config);
        settings.setUpdatedAt(LocalDateTime.now());
        tenantSettingsRepository.save(settings);
        return getSettings(tenantId);
    }

    @Transactional(readOnly = true)
    public LoyaltyTierConfig resolveTier(Long tenantId, int accumulatedPoints) {
        List<LoyaltyTierConfig> tiers = getSettings(tenantId).getTiers();
        LoyaltyTierConfig current = null;
        for (LoyaltyTierConfig tier : tiers) {
            if (!Boolean.FALSE.equals(tier.getActive()) && accumulatedPoints >= safeInt(tier.getMinPoints(), 0)) {
                current = tier;
            }
        }
        return current;
    }

    private int validateBonus(int value, String label) {
        if (value < 0 || value > MAX_BONUS_POINTS) {
            throw new RuntimeException("El bono de " + label + " debe estar entre 0 y " + MAX_BONUS_POINTS + " puntos.");
        }
        return value;
    }

    private List<LoyaltyTierConfig> validateTiers(List<LoyaltyTierConfig> input) {
        if (input.isEmpty()) throw new RuntimeException("Crea al menos una categoría de fidelización.");
        if (input.size() > MAX_TIERS) throw new RuntimeException("Puedes crear hasta " + MAX_TIERS + " categorías.");

        List<LoyaltyTierConfig> result = new ArrayList<>();
        Map<String, Boolean> names = new HashMap<>();
        Map<Integer, Boolean> thresholds = new HashMap<>();

        for (LoyaltyTierConfig raw : input) {
            String name = raw.getName() == null ? "" : raw.getName().trim();
            int minPoints = safeInt(raw.getMinPoints(), -1);
            if (name.isEmpty() || name.length() > 40) throw new RuntimeException("Cada categoría necesita un nombre de hasta 40 caracteres.");
            if (minPoints < 0 || minPoints > 100_000_000) throw new RuntimeException("Los puntos mínimos deben ser mayores o iguales a cero.");
            if (names.put(name.toLowerCase(), true) != null) throw new RuntimeException("No repitas el nombre de una categoría.");
            if (thresholds.put(minPoints, true) != null) throw new RuntimeException("No repitas los puntos mínimos entre categorías.");

            result.add(LoyaltyTierConfig.builder()
                    .id(cleanId(raw.getId()))
                    .name(name)
                    .minPoints(minPoints)
                    .colorHex(normalizeColor(raw.getColorHex()))
                    .iconName(trim(raw.getIconName(), 40))
                    .description(trim(raw.getDescription(), 160))
                    .active(!Boolean.FALSE.equals(raw.getActive()))
                    .build());
        }
        result.sort(Comparator.comparingInt(t -> safeInt(t.getMinPoints(), 0)));
        if (safeInt(result.get(0).getMinPoints(), -1) != 0) {
            throw new RuntimeException("La primera categoría debe comenzar en 0 puntos.");
        }
        return result;
    }

    private List<Map<String, Object>> serializeTiers(List<LoyaltyTierConfig> tiers) {
        return tiers.stream().map(tier -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", tier.getId());
            map.put("name", tier.getName());
            map.put("minPoints", tier.getMinPoints());
            map.put("colorHex", tier.getColorHex());
            map.put("iconName", tier.getIconName());
            map.put("description", tier.getDescription());
            map.put("active", tier.getActive());
            return map;
        }).toList();
    }

    private List<LoyaltyTierConfig> resolveTiers(TenantSettings settings) {
        Object raw = config(settings).get(TIERS_KEY);
        if (!(raw instanceof List<?> list) || list.isEmpty()) return defaultTiers();
        List<LoyaltyTierConfig> tiers = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) continue;
            tiers.add(LoyaltyTierConfig.builder()
                    .id(cleanId(stringValue(map.get("id"))))
                    .name(stringValue(map.get("name")))
                    .minPoints(intValue(map.get("minPoints"), 0))
                    .colorHex(normalizeColor(stringValue(map.get("colorHex"))))
                    .iconName(stringValue(map.get("iconName")))
                    .description(stringValue(map.get("description")))
                    .active(boolValue(map.get("active"), true))
                    .build());
        }
        if (tiers.isEmpty()) return defaultTiers();
        tiers.sort(Comparator.comparingInt(t -> safeInt(t.getMinPoints(), 0)));
        return tiers;
    }

    private List<LoyaltyTierConfig> defaultTiers() {
        return List.of(
                tier("bronze", "Bronce", 0, "#A16207", "workspace_premium", "Categoría inicial"),
                tier("silver", "Plata", 250, "#64748B", "workspace_premium", "Cliente en crecimiento"),
                tier("gold", "Oro", 400, "#D49B00", "military_tech", "Cliente frecuente"),
                tier("vip", "VIP", 500, "#6D28D9", "diamond", "Máxima categoría inicial")
        );
    }

    private LoyaltyTierConfig tier(String id, String name, int points, String color, String icon, String description) {
        return LoyaltyTierConfig.builder().id(id).name(name).minPoints(points).colorHex(color)
                .iconName(icon).description(description).active(true).build();
    }

    private TenantSettings resolveSettings(Long tenantId) {
        return tenantSettingsRepository.findByTenant_Id(tenantId)
                .orElseThrow(() -> new RuntimeException("No existe configuración del negocio."));
    }

    private BigDecimal resolvePointsPerCurrencyUnit(TenantSettings settings) {
        Object raw = config(settings).get(POINTS_PER_CURRENCY_UNIT_KEY);
        if (raw == null) return DEFAULT_POINTS_PER_CURRENCY_UNIT;
        try {
            BigDecimal value = new BigDecimal(raw.toString()).setScale(2, RoundingMode.HALF_UP);
            return value.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : value;
        } catch (Exception ignored) {
            return DEFAULT_POINTS_PER_CURRENCY_UNIT;
        }
    }

    private Map<String, Object> config(TenantSettings settings) {
        return settings.getScheduleConfig() == null ? Map.of() : settings.getScheduleConfig();
    }

    private boolean readBoolean(TenantSettings settings, String key, boolean fallback) {
        return boolValue(config(settings).get(key), fallback);
    }

    private int readInt(TenantSettings settings, String key, int fallback) {
        return intValue(config(settings).get(key), fallback);
    }

    private boolean boolValue(Object value, boolean fallback) {
        if (value == null) return fallback;
        if (value instanceof Boolean b) return b;
        return "true".equalsIgnoreCase(value.toString()) || (fallback && !"false".equalsIgnoreCase(value.toString()));
    }

    private int intValue(Object value, int fallback) {
        if (value == null) return fallback;
        try { return Integer.parseInt(value.toString()); } catch (Exception ignored) { return fallback; }
    }

    private int safeInt(Integer value, int fallback) { return value == null ? fallback : value; }
    private String stringValue(Object value) { return value == null ? "" : value.toString().trim(); }
    private String cleanId(String value) { return value == null || value.isBlank() ? UUID.randomUUID().toString() : value.trim(); }
    private String trim(String value, int max) { if (value == null) return null; String v = value.trim(); return v.isEmpty() ? null : v.substring(0, Math.min(v.length(), max)); }
    private String normalizeColor(String value) { return value != null && value.trim().matches("#[0-9A-Fa-f]{6}") ? value.trim().toUpperCase() : "#6D28D9"; }

    private String normalizeCurrency(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        String normalized = value.trim().toUpperCase();
        if (!normalized.matches("[A-Z]{3}")) throw new RuntimeException("La moneda debe tener un código ISO de 3 letras.");
        return normalized;
    }

    private String resolveCurrencySymbol(String currency) {
        return switch (normalizeCurrency(currency, "PEN")) {
            case "PEN" -> "S/";
            case "USD", "COP", "MXN", "CLP", "ARS" -> "$";
            case "BOB", "VES" -> "Bs";
            case "BRL" -> "R$";
            case "EUR" -> "EUR";
            default -> normalizeCurrency(currency, "PEN");
        };
    }
}
package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.UpdateLoyaltySettingsRequest;
import com.gods.saas.domain.dto.response.LoyaltySettingsResponse;
import com.gods.saas.domain.model.TenantSettings;
import com.gods.saas.domain.repository.TenantSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OwnerLoyaltySettingsService {

    private static final BigDecimal DEFAULT_POINTS_PER_CURRENCY_UNIT = BigDecimal.valueOf(5);
    private static final String POINTS_PER_CURRENCY_UNIT_KEY = "loyaltyPointsPerCurrencyUnit";

    private final TenantSettingsRepository tenantSettingsRepository;

    @Transactional(readOnly = true)
    public LoyaltySettingsResponse getSettings(Long tenantId) {
        TenantSettings settings = resolveSettings(tenantId);
        String currency = normalizeCurrency(settings.getCurrency(), "PEN");

        return LoyaltySettingsResponse.builder()
                .pointsPerCurrencyUnit(resolvePointsPerCurrencyUnit(settings))
                .currency(currency)
                .currencySymbol(resolveCurrencySymbol(currency))
                .build();
    }

    @Transactional
    public LoyaltySettingsResponse updateSettings(Long tenantId, UpdateLoyaltySettingsRequest request) {
        if (request == null) {
            throw new RuntimeException("Ingresa la cantidad de puntos por unidad monetaria.");
        }

        TenantSettings settings = resolveSettings(tenantId);

        if (request.getPointsPerCurrencyUnit() != null) {
            BigDecimal points = request.getPointsPerCurrencyUnit().setScale(2, RoundingMode.HALF_UP);

            if (points.compareTo(BigDecimal.ZERO) < 0) {
                throw new RuntimeException("Los puntos por unidad monetaria no pueden ser negativos.");
            }

            if (points.compareTo(BigDecimal.valueOf(1000)) > 0) {
                throw new RuntimeException("Ingresa un valor menor o igual a 1000 puntos.");
            }

            Map<String, Object> config = settings.getScheduleConfig() == null
                    ? new HashMap<>()
                    : new HashMap<>(settings.getScheduleConfig());

            config.put(POINTS_PER_CURRENCY_UNIT_KEY, points);
            settings.setScheduleConfig(config);
        }

        if (request.getCurrency() != null && !request.getCurrency().trim().isEmpty()) {
            settings.setCurrency(normalizeCurrency(request.getCurrency(), "PEN"));
        }

        settings.setUpdatedAt(LocalDateTime.now());
        tenantSettingsRepository.save(settings);

        return getSettings(tenantId);
    }

    private TenantSettings resolveSettings(Long tenantId) {
        return tenantSettingsRepository.findByTenant_Id(tenantId)
                .orElseThrow(() -> new RuntimeException("No existe configuracion del negocio."));
    }

    private BigDecimal resolvePointsPerCurrencyUnit(TenantSettings settings) {
        Map<String, Object> config = settings.getScheduleConfig();
        if (config == null) {
            return DEFAULT_POINTS_PER_CURRENCY_UNIT;
        }

        Object raw = config.get(POINTS_PER_CURRENCY_UNIT_KEY);
        if (raw == null) {
            return DEFAULT_POINTS_PER_CURRENCY_UNIT;
        }

        try {
            BigDecimal value = new BigDecimal(raw.toString()).setScale(2, RoundingMode.HALF_UP);
            return value.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : value;
        } catch (Exception ignored) {
            return DEFAULT_POINTS_PER_CURRENCY_UNIT;
        }
    }

    private String normalizeCurrency(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }

        String normalized = value.trim().toUpperCase();
        if (!normalized.matches("[A-Z]{3}")) {
            throw new RuntimeException("La moneda debe tener un codigo ISO de 3 letras.");
        }

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

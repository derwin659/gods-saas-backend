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

        return LoyaltySettingsResponse.builder()
                .pointsPerCurrencyUnit(resolvePointsPerCurrencyUnit(settings))
                .currency(clean(settings.getCurrency(), "PEN"))
                .build();
    }

    @Transactional
    public LoyaltySettingsResponse updateSettings(Long tenantId, UpdateLoyaltySettingsRequest request) {
        if (request == null || request.getPointsPerCurrencyUnit() == null) {
            throw new RuntimeException("Ingresa la cantidad de puntos por unidad monetaria.");
        }

        BigDecimal points = request.getPointsPerCurrencyUnit().setScale(2, RoundingMode.HALF_UP);

        if (points.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("Los puntos por unidad monetaria no pueden ser negativos.");
        }

        if (points.compareTo(BigDecimal.valueOf(1000)) > 0) {
            throw new RuntimeException("Ingresa un valor menor o igual a 1000 puntos.");
        }

        TenantSettings settings = resolveSettings(tenantId);
        Map<String, Object> config = settings.getScheduleConfig() == null
                ? new HashMap<>()
                : new HashMap<>(settings.getScheduleConfig());

        config.put(POINTS_PER_CURRENCY_UNIT_KEY, points);
        settings.setScheduleConfig(config);
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

    private String clean(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }
}

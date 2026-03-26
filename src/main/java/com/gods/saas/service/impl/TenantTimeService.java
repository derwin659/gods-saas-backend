package com.gods.saas.service.impl;

import com.gods.saas.domain.model.TenantSettings;
import com.gods.saas.domain.repository.TenantSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class TenantTimeService {

    private final TenantSettingsRepository tenantSettingsRepository;

    public ZoneId getZone(Long tenantId) {
        String timezone = tenantSettingsRepository.findByTenantId(tenantId)
                .map(TenantSettings::getTimezone)
                .filter(tz -> tz != null && !tz.isBlank())
                .orElse("America/Lima");

        return ZoneId.of(timezone);
    }

    public LocalDateTime now(Long tenantId) {
        return LocalDateTime.now(getZone(tenantId));
    }

    public LocalDate today(Long tenantId) {
        return LocalDate.now(getZone(tenantId));
    }

    public LocalTime currentTime(Long tenantId) {
        return LocalTime.now(getZone(tenantId));
    }
}
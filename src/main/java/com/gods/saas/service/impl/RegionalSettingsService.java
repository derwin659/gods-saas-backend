package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.UpdateRegionalSettingsRequest;
import com.gods.saas.domain.dto.response.RegionalSettingsResponse;
import com.gods.saas.domain.model.AppUser;
import com.gods.saas.domain.model.Tenant;
import com.gods.saas.domain.model.TenantSettings;
import com.gods.saas.domain.repository.AppUserRepository;
import com.gods.saas.domain.repository.TenantRepository;
import com.gods.saas.domain.repository.TenantSettingsRepository;
import com.gods.saas.utils.RegionalDefaults;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class RegionalSettingsService {

    private final TenantRepository tenantRepository;
    private final TenantSettingsRepository tenantSettingsRepository;
    private final AppUserRepository appUserRepository;

    public RegionalSettingsResponse get(Long tenantId, Long userId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Negocio no encontrado"));
        TenantSettings settings = tenantSettingsRepository.findByTenantId(tenantId).orElse(null);
        AppUser user = userId == null ? null : appUserRepository.findById(userId).orElse(null);
        return build(tenant, settings, user);
    }

    @Transactional
    public RegionalSettingsResponse update(
            Long tenantId,
            Long userId,
            UpdateRegionalSettingsRequest request
    ) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Negocio no encontrado"));
        TenantSettings settings = tenantSettingsRepository.findByTenantId(tenantId)
                .orElseGet(() -> {
                    TenantSettings created = new TenantSettings();
                    created.setTenant(tenant);
                    created.setCreatedAt(LocalDateTime.now());
                    return created;
                });

        if (request != null && clean(request.getLanguage()) != null) {
            settings.setLanguage(RegionalDefaults.normalizeLocale(request.getLanguage(), tenant.getPais()));
        }
        if (request != null && clean(request.getTimezone()) != null) {
            try {
                settings.setTimezone(ZoneId.of(request.getTimezone().trim()).getId());
            } catch (RuntimeException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Zona horaria no valida");
            }
        }
        if (request != null && clean(request.getCurrency()) != null) {
            String currency = request.getCurrency().trim().toUpperCase(Locale.ROOT);
            if (!currency.matches("[A-Z]{3}")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Moneda no valida");
            }
            settings.setCurrency(currency);
        }
        settings.setUpdatedAt(LocalDateTime.now());
        settings = tenantSettingsRepository.save(settings);

        AppUser user = userId == null ? null : appUserRepository.findById(userId).orElse(null);
        if (user != null && request != null && clean(request.getPreferredLocale()) != null) {
            user.setPreferredLocale(RegionalDefaults.normalizeLocale(
                    request.getPreferredLocale(),
                    tenant.getPais()
            ));
            user.setFechaActualizacion(LocalDateTime.now());
            user = appUserRepository.save(user);
        }
        return build(tenant, settings, user);
    }

    private RegionalSettingsResponse build(Tenant tenant, TenantSettings settings, AppUser user) {
        String tenantLocale = RegionalDefaults.normalizeLocale(
                settings == null ? null : settings.getLanguage(),
                tenant.getPais()
        );
        String preferred = user == null ? null : clean(user.getPreferredLocale());
        return RegionalSettingsResponse.builder()
                .language(tenantLocale)
                .preferredLocale(preferred)
                .effectiveLocale(RegionalDefaults.normalizeLocale(preferred, tenant.getPais()))
                .timezone(RegionalDefaults.validTimezoneOrDefault(
                        settings == null ? null : settings.getTimezone(),
                        tenant.getPais()
                ))
                .currency(settings == null || clean(settings.getCurrency()) == null
                        ? "PEN" : settings.getCurrency().trim().toUpperCase(Locale.ROOT))
                .country(tenant.getPais())
                .supportedLocales(List.of(
                        new RegionalSettingsResponse.LocaleOption("es-PE", "Español"),
                        new RegionalSettingsResponse.LocaleOption("pt-BR", "Português (Brasil)"),
                        new RegionalSettingsResponse.LocaleOption("en-US", "English (United States)")
                ))
                .build();
    }

    private String clean(String value) {
        if (value == null) return null;
        String result = value.trim();
        return result.isEmpty() ? null : result;
    }
}

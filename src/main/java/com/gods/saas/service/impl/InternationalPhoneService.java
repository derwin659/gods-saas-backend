package com.gods.saas.service.impl;

import com.gods.saas.domain.model.Tenant;
import com.gods.saas.domain.repository.TenantSettingsRepository;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class InternationalPhoneService {

    private static final PhoneNumberUtil PHONE_UTIL = PhoneNumberUtil.getInstance();
    private static final String DEFAULT_REGION = "PE";

    private final TenantSettingsRepository tenantSettingsRepository;

    public NormalizedPhone normalize(Tenant tenant, String rawPhone) {
        String input = rawPhone == null ? "" : rawPhone.trim();
        if (input.isBlank()) {
            throw invalidPhone();
        }

        String digits = input.replaceAll("[^0-9]", "");
        if (input.startsWith("00") && digits.length() > 2) {
            input = "+" + digits.substring(2);
        }

        String tenantRegion = resolveTenantRegion(tenant);
        try {
            Phonenumber.PhoneNumber parsed = PHONE_UTIL.parse(
                    input,
                    input.startsWith("+") ? null : tenantRegion
            );
            if (!PHONE_UTIL.isPossibleNumber(parsed) || !PHONE_UTIL.isValidNumber(parsed)) {
                throw invalidPhone();
            }

            String e164 = PHONE_UTIL.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164);
            String region = PHONE_UTIL.getRegionCodeForNumber(parsed);
            if (region == null || region.isBlank()) {
                region = tenantRegion;
            }

            Set<String> lookupDigits = new LinkedHashSet<>();
            lookupDigits.add(e164.substring(1));
            lookupDigits.add(PHONE_UTIL.getNationalSignificantNumber(parsed));
            lookupDigits.add(PHONE_UTIL
                    .format(parsed, PhoneNumberUtil.PhoneNumberFormat.NATIONAL)
                    .replaceAll("[^0-9]", ""));
            lookupDigits.removeIf(value -> value == null || value.isBlank());

            return new NormalizedPhone(
                    e164,
                    e164.substring(1),
                    PHONE_UTIL.getNationalSignificantNumber(parsed),
                    lookupDigits,
                    region,
                    tenantRegion
            );
        } catch (NumberParseException exception) {
            throw invalidPhone();
        }
    }

    public String normalizeDigitsOrNull(Tenant tenant, String rawPhone) {
        try {
            return normalize(tenant, rawPhone).internationalDigits();
        } catch (ResponseStatusException exception) {
            return null;
        }
    }

    public String resolveTenantRegion(Tenant tenant) {
        String fromCountry = countryToRegion(tenant == null ? null : tenant.getPais());
        if (fromCountry != null) {
            return fromCountry;
        }

        if (tenant != null && tenant.getId() != null) {
            return tenantSettingsRepository.findByTenant_Id(tenant.getId())
                    .map(settings -> {
                        Map<String, Object> config = settings.getScheduleConfig();
                        if (config != null) {
                            for (String key : new String[]{
                                    "phoneCountry", "phoneCountryCode", "countryCode", "country", "pais"
                            }) {
                                Object value = config.get(key);
                                String region = countryToRegion(value == null ? null : value.toString());
                                if (region != null) return region;
                            }
                        }

                        String fromTimezone = regionFromTimezone(settings.getTimezone());
                        if (fromTimezone != null) return fromTimezone;
                        return regionFromCurrency(settings.getCurrency());
                    })
                    .orElse(DEFAULT_REGION);
        }

        return DEFAULT_REGION;
    }

    private String countryToRegion(String country) {
        String normalized = normalizeCountry(country);
        if (normalized == null) return null;
        if (normalized.length() == 2 && PHONE_UTIL.getCountryCodeForRegion(normalized) > 0) {
            return normalized;
        }

        for (String iso : Locale.getISOCountries()) {
            Locale locale = new Locale("", iso);
            if (normalized.equals(normalizeCountry(locale.getDisplayCountry(new Locale("es"))))
                    || normalized.equals(normalizeCountry(locale.getDisplayCountry(Locale.ENGLISH)))) {
                return iso;
            }
        }

        return switch (normalized) {
            case "UK", "INGLATERRA", "GRAN BRETANA" -> "GB";
            case "EEUU", "EE UU", "USA" -> "US";
            case "HOLANDA", "PAISES BAJOS" -> "NL";
            case "CHEQUIA", "REPUBLICA CHECA" -> "CZ";
            case "MACEDONIA", "MACEDONIA DEL NORTE" -> "MK";
            case "VATICANO" -> "VA";
            default -> null;
        };
    }

    private String regionFromTimezone(String timezone) {
        if (timezone == null) return null;
        return switch (timezone.trim()) {
            case "America/Lima" -> "PE";
            case "America/Bogota" -> "CO";
            case "America/Caracas" -> "VE";
            case "America/Guayaquil" -> "EC";
            case "America/Santiago" -> "CL";
            case "America/Argentina/Buenos_Aires" -> "AR";
            case "America/La_Paz" -> "BO";
            case "America/Sao_Paulo" -> "BR";
            case "America/Montevideo" -> "UY";
            case "America/Asuncion" -> "PY";
            case "America/Panama" -> "PA";
            case "America/Costa_Rica" -> "CR";
            case "America/Managua" -> "NI";
            case "America/Tegucigalpa" -> "HN";
            case "America/El_Salvador" -> "SV";
            case "America/Guatemala" -> "GT";
            case "America/Belize" -> "BZ";
            case "America/Mexico_City" -> "MX";
            case "Europe/Madrid" -> "ES";
            case "Europe/Lisbon" -> "PT";
            case "Europe/Paris" -> "FR";
            case "Europe/Rome" -> "IT";
            case "Europe/Berlin" -> "DE";
            case "Europe/London" -> "GB";
            default -> null;
        };
    }

    private String regionFromCurrency(String currency) {
        String normalized = normalizeCountry(currency);
        if (normalized == null) return null;
        return switch (normalized) {
            case "PEN" -> "PE";
            case "COP" -> "CO";
            case "VES" -> "VE";
            case "MXN" -> "MX";
            case "CLP" -> "CL";
            case "ARS" -> "AR";
            case "BOB" -> "BO";
            case "BRL" -> "BR";
            case "UYU" -> "UY";
            case "PYG" -> "PY";
            default -> null;
        };
    }

    private String normalizeCountry(String value) {
        if (value == null || value.isBlank()) return null;
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    private ResponseStatusException invalidPhone() {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Ingresa un numero celular valido con su pais."
        );
    }

    public record NormalizedPhone(
            String e164,
            String internationalDigits,
            String nationalDigits,
            Set<String> lookupDigits,
            String regionCode,
            String tenantRegionCode
    ) {
        public boolean belongsToTenantRegion() {
            if (regionCode == null || tenantRegionCode == null) return false;
            if (regionCode.equalsIgnoreCase(tenantRegionCode)) return true;
            return PHONE_UTIL.getCountryCodeForRegion(regionCode)
                    == PHONE_UTIL.getCountryCodeForRegion(tenantRegionCode);
        }
    }
}

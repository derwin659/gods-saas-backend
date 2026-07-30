package com.gods.saas.utils;

import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Set;

public final class RegionalDefaults {

    public static final String DEFAULT_LOCALE = "es-PE";
    public static final String DEFAULT_TIMEZONE = "America/Lima";
    private static final Set<String> SUPPORTED_LOCALES = Set.of("es-PE", "pt-BR", "en-US");

    private RegionalDefaults() {
    }

    public static String normalizeLocale(String value, String country) {
        String cleaned = clean(value);
        if (cleaned == null) return localeForCountry(country);

        String tag = cleaned.replace('_', '-');
        Locale parsed = Locale.forLanguageTag(tag);
        String language = parsed.getLanguage().toLowerCase(Locale.ROOT);
        return switch (language) {
            case "pt" -> "pt-BR";
            case "en" -> "en-US";
            case "es" -> "es-PE";
            default -> localeForCountry(country);
        };
    }

    public static String localeForCountry(String country) {
        String value = normalizeCountry(country);
        if (Set.of("BR", "BRA", "BRASIL", "BRAZIL").contains(value)) return "pt-BR";
        if (Set.of("US", "USA", "UNITEDSTATES", "ESTADOSUNIDOS").contains(value)) return "en-US";
        return DEFAULT_LOCALE;
    }

    public static String timezoneForCountry(String country) {
        String value = normalizeCountry(country);
        return switch (value) {
            case "BR", "BRA", "BRASIL", "BRAZIL" -> "America/Sao_Paulo";
            case "US", "USA", "UNITEDSTATES", "ESTADOSUNIDOS" -> "America/New_York";
            case "CO", "COL", "COLOMBIA" -> "America/Bogota";
            case "VE", "VEN", "VENEZUELA" -> "America/Caracas";
            case "AR", "ARG", "ARGENTINA" -> "America/Argentina/Buenos_Aires";
            case "CL", "CHL", "CHILE" -> "America/Santiago";
            case "EC", "ECU", "ECUADOR" -> "America/Guayaquil";
            case "BO", "BOL", "BOLIVIA" -> "America/La_Paz";
            case "PY", "PRY", "PARAGUAY" -> "America/Asuncion";
            case "UY", "URY", "URUGUAY" -> "America/Montevideo";
            case "MX", "MEX", "MEXICO" -> "America/Mexico_City";
            case "PA", "PAN", "PANAMA" -> "America/Panama";
            case "CR", "CRI", "COSTARICA" -> "America/Costa_Rica";
            case "GT", "GTM", "GUATEMALA" -> "America/Guatemala";
            case "SV", "SLV", "ELSALVADOR" -> "America/El_Salvador";
            case "HN", "HND", "HONDURAS" -> "America/Tegucigalpa";
            case "NI", "NIC", "NICARAGUA" -> "America/Managua";
            case "DO", "DOM", "REPUBLICADOMINICANA" -> "America/Santo_Domingo";
            case "ES", "ESP", "ESPANA", "SPAIN" -> "Europe/Madrid";
            case "PT", "PRT", "PORTUGAL" -> "Europe/Lisbon";
            case "GB", "GBR", "UNITEDKINGDOM", "REINOUNIDO" -> "Europe/London";
            case "FR", "FRA", "FRANCE", "FRANCIA" -> "Europe/Paris";
            case "DE", "DEU", "GERMANY", "ALEMANIA" -> "Europe/Berlin";
            case "IT", "ITA", "ITALY", "ITALIA" -> "Europe/Rome";
            default -> DEFAULT_TIMEZONE;
        };
    }

    public static String validTimezoneOrDefault(String timezone, String country) {
        String candidate = clean(timezone);
        if (candidate == null) candidate = timezoneForCountry(country);
        try {
            return ZoneId.of(candidate).getId();
        } catch (DateTimeException exception) {
            return timezoneForCountry(country);
        }
    }

    public static boolean isSupportedLocale(String locale) {
        return SUPPORTED_LOCALES.contains(normalizeLocale(locale, null));
    }

    private static String normalizeCountry(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalized.replaceAll("[^A-Za-z]", "").toUpperCase(Locale.ROOT);
    }

    private static String clean(String value) {
        if (value == null) return null;
        String result = value.trim();
        return result.isEmpty() ? null : result;
    }
}

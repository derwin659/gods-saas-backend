package com.gods.saas.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gods.saas.domain.dto.request.PaymentMethodConfigRequest;
import com.gods.saas.domain.dto.request.UpdateBookingPaymentSettingsRequest;
import com.gods.saas.domain.dto.response.BookingPaymentSettingsResponse;
import com.gods.saas.domain.dto.response.PaymentMethodConfigResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OwnerBookingPaymentSettingsService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public BookingPaymentSettingsResponse getSettings(Long tenantId) {
        Map<String, Object> scheduleConfig = loadScheduleConfig(tenantId);
        List<PaymentMethodConfigResponse> paymentMethods = loadPaymentMethods(tenantId);

        return BookingPaymentSettingsResponse.builder()
                .bookingDepositEnabled(asBoolean(scheduleConfig.get("bookingDepositEnabled"), false))
                .bookingDepositMode(asString(scheduleConfig.get("bookingDepositMode"), "FIXED"))
                .bookingDepositDefaultAmount(asBigDecimal(scheduleConfig.get("bookingDepositDefaultAmount"), BigDecimal.ZERO))
                .bookingDepositDefaultPercent(asInteger(scheduleConfig.get("bookingDepositDefaultPercent"), null))
                .paymentMethods(paymentMethods)
                .build();
    }

    @Transactional
    public BookingPaymentSettingsResponse updateSettings(
            Long tenantId,
            UpdateBookingPaymentSettingsRequest request
    ) {
        if (request == null) {
            throw new RuntimeException("Solicitud inválida");
        }

        boolean enabled = Boolean.TRUE.equals(request.getBookingDepositEnabled());
        String mode = normalizeMode(request.getBookingDepositMode());

        BigDecimal fixedAmount = request.getBookingDepositDefaultAmount() == null
                ? BigDecimal.ZERO
                : request.getBookingDepositDefaultAmount();

        Integer percent = request.getBookingDepositDefaultPercent();

        validateDepositConfig(enabled, mode, fixedAmount, percent);

        Map<String, Object> scheduleConfig = loadScheduleConfig(tenantId);

        scheduleConfig.put("bookingDepositEnabled", enabled);
        scheduleConfig.put("bookingDepositMode", mode);

        if ("FIXED".equals(mode)) {
            scheduleConfig.put("bookingDepositDefaultAmount", fixedAmount);
            scheduleConfig.put("bookingDepositDefaultPercent", null);
        } else {
            scheduleConfig.put("bookingDepositDefaultAmount", BigDecimal.ZERO);
            scheduleConfig.put("bookingDepositDefaultPercent", percent);
        }

        saveScheduleConfig(tenantId, scheduleConfig);

        if (request.getPaymentMethods() != null) {
            savePaymentMethods(tenantId, request.getPaymentMethods());
        }

        if (enabled) {
            long activeMethods = countActivePaymentMethods(tenantId);
            if (activeMethods <= 0) {
                throw new RuntimeException("Debes activar al menos un método de pago para usar reservas con inicial");
            }
        }

        return getSettings(tenantId);
    }

    private Map<String, Object> loadScheduleConfig(Long tenantId) {
        String json = jdbcTemplate.query(
                """
                SELECT COALESCE(schedule_config::text, '{}')
                FROM tenant_settings
                WHERE tenant_id = ?
                """,
                rs -> rs.next() ? rs.getString(1) : null,
                tenantId
        );

        if (json == null) {
            throw new RuntimeException("No existe configuración del tenant. Revisa tenant_settings para tenant_id=" + tenantId);
        }

        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private void saveScheduleConfig(Long tenantId, Map<String, Object> scheduleConfig) {
        try {
            String json = objectMapper.writeValueAsString(scheduleConfig);

            int updated = jdbcTemplate.update(
                    """
                    UPDATE tenant_settings
                    SET schedule_config = ?::jsonb
                    WHERE tenant_id = ?
                    """,
                    json,
                    tenantId
            );

            if (updated <= 0) {
                throw new RuntimeException("No se pudo actualizar tenant_settings para tenant_id=" + tenantId);
            }
        } catch (Exception e) {
            throw new RuntimeException("No se pudo guardar configuración de reservas: " + e.getMessage());
        }
    }

    private List<PaymentMethodConfigResponse> loadPaymentMethods(Long tenantId) {
        return jdbcTemplate.query(
                """
                SELECT
                    payment_method_id,
                    branch_id,
                    code,
                    display_name,
                    country_code,
                    instructions,
                    account_label,
                    account_value,
                    qr_image_url,
                    requires_operation_code,
                    requires_evidence,
                    active,
                    sort_order
                FROM tenant_payment_method
                WHERE tenant_id = ?
                ORDER BY COALESCE(sort_order, 999), display_name
                """,
                (rs, rowNum) -> PaymentMethodConfigResponse.builder()
                        .id(rs.getLong("payment_method_id"))
                        .branchId(rs.getObject("branch_id") == null ? null : rs.getLong("branch_id"))
                        .code(rs.getString("code"))
                        .displayName(rs.getString("display_name"))
                        .countryCode(rs.getString("country_code"))
                        .instructions(rs.getString("instructions"))
                        .accountLabel(rs.getString("account_label"))
                        .accountValue(rs.getString("account_value"))
                        .qrImageUrl(rs.getString("qr_image_url"))
                        .requiresOperationCode(rs.getBoolean("requires_operation_code"))
                        .requiresEvidence(rs.getBoolean("requires_evidence"))
                        .active(rs.getBoolean("active"))
                        .sortOrder(rs.getObject("sort_order") == null ? null : rs.getInt("sort_order"))
                        .build(),
                tenantId
        );
    }

    private void savePaymentMethods(Long tenantId, List<PaymentMethodConfigRequest> methods) {
        int index = 1;

        for (PaymentMethodConfigRequest method : methods) {
            if (method == null) continue;

            String code = normalizeCode(method.getCode());
            String displayName = trimToNull(method.getDisplayName());

            if (code == null) {
                throw new RuntimeException("El código del método de pago es obligatorio");
            }

            if (displayName == null) {
                displayName = code;
            }

            int sortOrder = method.getSortOrder() == null ? index : method.getSortOrder();

            if (method.getId() != null && method.getId() > 0) {
                updatePaymentMethod(tenantId, method, code, displayName, sortOrder);
            } else {
                insertPaymentMethod(tenantId, method, code, displayName, sortOrder);
            }

            index++;
        }
    }

    private void updatePaymentMethod(
            Long tenantId,
            PaymentMethodConfigRequest method,
            String code,
            String displayName,
            int sortOrder
    ) {
        int updated = jdbcTemplate.update(
                """
                UPDATE tenant_payment_method
                SET
                    branch_id = ?,
                    code = ?,
                    display_name = ?,
                    country_code = ?,
                    instructions = ?,
                    account_label = ?,
                    account_value = ?,
                    qr_image_url = ?,
                    requires_operation_code = ?,
                    requires_evidence = ?,
                    active = ?,
                    sort_order = ?
                WHERE payment_method_id = ?
                AND tenant_id = ?
                """,
                method.getBranchId(),
                code,
                displayName,
                normalizeCountry(method.getCountryCode()),
                trimToNull(method.getInstructions()),
                trimToNull(method.getAccountLabel()),
                trimToNull(method.getAccountValue()),
                trimToNull(method.getQrImageUrl()),
                Boolean.TRUE.equals(method.getRequiresOperationCode()),
                Boolean.TRUE.equals(method.getRequiresEvidence()),
                method.getActive() == null || Boolean.TRUE.equals(method.getActive()),
                sortOrder,
                method.getId(),
                tenantId
        );

        if (updated <= 0) {
            throw new RuntimeException("No se pudo actualizar el método de pago: " + displayName);
        }
    }

    private void insertPaymentMethod(
            Long tenantId,
            PaymentMethodConfigRequest method,
            String code,
            String displayName,
            int sortOrder
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO tenant_payment_method (
                    tenant_id,
                    branch_id,
                    code,
                    display_name,
                    country_code,
                    instructions,
                    account_label,
                    account_value,
                    qr_image_url,
                    requires_operation_code,
                    requires_evidence,
                    active,
                    sort_order
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                tenantId,
                method.getBranchId(),
                code,
                displayName,
                normalizeCountry(method.getCountryCode()),
                trimToNull(method.getInstructions()),
                trimToNull(method.getAccountLabel()),
                trimToNull(method.getAccountValue()),
                trimToNull(method.getQrImageUrl()),
                Boolean.TRUE.equals(method.getRequiresOperationCode()),
                Boolean.TRUE.equals(method.getRequiresEvidence()),
                method.getActive() == null || Boolean.TRUE.equals(method.getActive()),
                sortOrder
        );
    }

    private long countActivePaymentMethods(Long tenantId) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM tenant_payment_method
                WHERE tenant_id = ?
                AND active = true
                """,
                Long.class,
                tenantId
        );

        return count == null ? 0 : count;
    }

    private void validateDepositConfig(
            boolean enabled,
            String mode,
            BigDecimal fixedAmount,
            Integer percent
    ) {
        if (!enabled) return;

        if (!"FIXED".equals(mode) && !"PERCENT".equals(mode)) {
            throw new RuntimeException("Modo de inicial no válido. Usa FIXED o PERCENT");
        }

        if ("FIXED".equals(mode)) {
            if (fixedAmount == null || fixedAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("El monto fijo del inicial debe ser mayor a cero");
            }
        }

        if ("PERCENT".equals(mode)) {
            if (percent == null || percent <= 0 || percent > 100) {
                throw new RuntimeException("El porcentaje del inicial debe estar entre 1 y 100");
            }
        }
    }

    private String normalizeMode(String value) {
        if (value == null || value.isBlank()) {
            return "FIXED";
        }

        String mode = value.trim().toUpperCase();

        if ("MONTO".equals(mode) || "FIJO".equals(mode)) {
            return "FIXED";
        }

        if ("PORCENTAJE".equals(mode)) {
            return "PERCENT";
        }

        return mode;
    }

    private String normalizeCode(String value) {
        String text = trimToNull(value);
        if (text == null) return null;

        return text
                .trim()
                .toUpperCase()
                .replace(" ", "_")
                .replace("Ó", "O");
    }

    private String normalizeCountry(String value) {
        String text = trimToNull(value);
        if (text == null) return null;
        return text.toUpperCase();
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    private Boolean asBoolean(Object value, boolean fallback) {
        if (value == null) return fallback;
        if (value instanceof Boolean b) return b;
        return Boolean.parseBoolean(value.toString());
    }

    private String asString(Object value, String fallback) {
        if (value == null) return fallback;
        String text = value.toString().trim();
        return text.isEmpty() ? fallback : text;
    }

    private Integer asInteger(Object value, Integer fallback) {
        if (value == null) return fallback;
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return fallback;
        }
    }

    private BigDecimal asBigDecimal(Object value, BigDecimal fallback) {
        if (value == null) return fallback;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());

        try {
            return new BigDecimal(value.toString());
        } catch (Exception e) {
            return fallback;
        }
    }
}
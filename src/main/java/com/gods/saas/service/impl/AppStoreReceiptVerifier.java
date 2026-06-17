package com.gods.saas.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gods.saas.exception.BusinessException;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AppStoreReceiptVerifier {

    private static final String PRODUCTION_URL = "https://buy.itunes.apple.com/verifyReceipt";
    private static final String SANDBOX_URL = "https://sandbox.itunes.apple.com/verifyReceipt";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    public VerifiedReceipt verify(String expectedProductId, String receiptData) {
        String receipt = receiptData == null ? "" : receiptData.trim();
        if (receipt.isBlank()) {
            throw new BusinessException("APP_STORE_RECEIPT_REQUIRED", "Recibo App Store obligatorio");
        }

        Map<String, Object> production = postReceipt(PRODUCTION_URL, receipt);
        int status = intValue(production.get("status"));
        Map<String, Object> response = production;
        String environmentName = "PRODUCTION";

        if (status == 21007) {
            response = postReceipt(SANDBOX_URL, receipt);
            status = intValue(response.get("status"));
            environmentName = "SANDBOX";
        }

        if (status != 0) {
            throw new BusinessException(
                    "APP_STORE_RECEIPT_INVALID",
                    "Apple no valido el recibo. Codigo: " + status
            );
        }

        Map<String, Object> latest = latestReceiptItem(response, expectedProductId);
        String productId = stringValue(latest.get("product_id"));
        if (!expectedProductId.equals(productId)) {
            throw new BusinessException(
                    "APP_STORE_PRODUCT_MISMATCH",
                    "El producto comprado no coincide con el plan solicitado"
            );
        }

        LocalDateTime expiresAt = millisToDateTime(latest.get("expires_date_ms"));
        if (expiresAt != null && expiresAt.isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new BusinessException("APP_STORE_SUBSCRIPTION_EXPIRED", "La suscripcion de App Store esta vencida");
        }

        return VerifiedReceipt.builder()
                .productId(productId)
                .transactionId(stringValue(latest.get("transaction_id")))
                .originalTransactionId(stringValue(latest.get("original_transaction_id")))
                .purchasedAt(millisToDateTime(latest.get("purchase_date_ms")))
                .expiresAt(expiresAt)
                .environment(environmentName)
                .rawResponse(toJson(response))
                .build();
    }

    private Map<String, Object> postReceipt(String url, String receiptData) {
        Map<String, Object> body = new HashMap<>();
        body.put("receipt-data", receiptData);
        body.put("exclude-old-transactions", true);

        String sharedSecret = readProperty("appstore.shared-secret");
        if (sharedSecret.isBlank()) {
            sharedSecret = readProperty("APP_STORE_SHARED_SECRET");
        }
        if (sharedSecret.isBlank()) {
            throw new BusinessException(
                    "APP_STORE_SHARED_SECRET_MISSING",
                    "Falta configurar APP_STORE_SHARED_SECRET para validar compras App Store"
            );
        }
        body.put("password", sharedSecret);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.postForObject(url, body, Map.class);
        return response == null ? Map.of("status", -1) : response;
    }

    private Map<String, Object> latestReceiptItem(Map<String, Object> response, String expectedProductId) {
        Object rawItems = response.get("latest_receipt_info");
        if (!(rawItems instanceof List<?> list) || list.isEmpty()) {
            throw new BusinessException("APP_STORE_RECEIPT_EMPTY", "El recibo App Store no contiene suscripcion");
        }

        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) item;
                    return map;
                })
                .filter(item -> expectedProductId.equals(stringValue(item.get("product_id"))))
                .max(Comparator.comparingLong(item -> longValue(item.get("expires_date_ms"))))
                .orElseThrow(() -> new BusinessException(
                        "APP_STORE_PRODUCT_NOT_FOUND",
                        "El recibo no contiene el producto " + expectedProductId
                ));
    }

    private String readProperty(String key) {
        String value = environment.getProperty(key, "");
        if (value != null && !value.trim().isBlank()) return value.trim();
        String envKey = key.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_');
        value = environment.getProperty(envKey, "");
        if (value != null && !value.trim().isBlank()) return value.trim();
        value = System.getenv(envKey);
        return value == null ? "" : value.trim();
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) return number.intValue();
        if (value == null) return -1;
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null) return 0L;
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static LocalDateTime millisToDateTime(Object value) {
        long millis = longValue(value);
        if (millis <= 0) return null;
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC);
    }

    @Getter
    @Builder
    public static class VerifiedReceipt {
        private String productId;
        private String transactionId;
        private String originalTransactionId;
        private LocalDateTime purchasedAt;
        private LocalDateTime expiresAt;
        private String environment;
        private String rawResponse;
    }
}

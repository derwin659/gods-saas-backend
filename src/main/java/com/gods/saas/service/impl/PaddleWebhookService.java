package com.gods.saas.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gods.saas.domain.model.PaddleWebhookEvent;
import com.gods.saas.domain.model.Subscription;
import com.gods.saas.domain.model.SubscriptionPayment;
import com.gods.saas.domain.repository.PaddleWebhookEventRepository;
import com.gods.saas.domain.repository.SubscriptionPaymentRepository;
import com.gods.saas.domain.repository.SuscriptionRepository;
import com.gods.saas.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PaddleWebhookService {

    private static final String PROVIDER = "PADDLE";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_PROCESSED = "PROCESSED";
    private static final Set<String> ZERO_DECIMAL_CURRENCIES = Set.of(
            "BIF", "CLP", "COP", "DJF", "GNF", "JPY", "KMF", "KRW",
            "PYG", "RWF", "UGX", "VND", "VUV", "XAF", "XOF", "XPF"
    );

    private final ObjectMapper objectMapper;
    private final Environment environment;
    private final PaddleWebhookEventRepository webhookEventRepository;
    private final SuscriptionRepository subscriptionRepository;
    private final SubscriptionPaymentRepository paymentRepository;
    private final SubscriptionPlanPricingService pricingService;

    @Transactional
    public void handleWebhook(String rawBody, String signatureHeader) {
        verifySignature(rawBody, signatureHeader);

        JsonNode payload = readJson(rawBody);
        String eventId = text(payload, "event_id");
        String eventType = text(payload, "event_type");
        if (eventType.isBlank()) eventType = text(payload, "name");

        if (eventId.isBlank()) {
            throw new BusinessException("PADDLE_WEBHOOK_INVALID", "Webhook de Paddle sin event_id.");
        }

        if (webhookEventRepository.findByEventId(eventId).isPresent()) {
            return;
        }

        JsonNode data = payload.path("data");
        String objectId = text(data, "id");
        Long tenantId = tenantIdFrom(data);
        LocalDateTime now = LocalDateTime.now();

        PaddleWebhookEvent event = webhookEventRepository.save(
                PaddleWebhookEvent.builder()
                        .eventId(eventId)
                        .eventType(eventType)
                        .paddleObjectId(objectId)
                        .tenantId(tenantId)
                        .status("RECEIVED")
                        .payload(rawBody)
                        .receivedAt(now)
                        .build()
        );

        try {
            processEvent(eventType, data, tenantId);
            event.setStatus(STATUS_PROCESSED);
        } catch (Exception ex) {
            event.setStatus(STATUS_FAILED);
            event.setErrorMessage(ex.getMessage());
            throw ex;
        } finally {
            event.setProcessedAt(LocalDateTime.now());
            webhookEventRepository.save(event);
        }
    }

    private void processEvent(String eventType, JsonNode data, Long tenantId) {
        String normalized = cleanUpper(eventType);

        switch (normalized) {
            case "TRANSACTION.COMPLETED", "SUBSCRIPTION.CREATED", "SUBSCRIPTION.ACTIVATED" ->
                    activateOrRenew(data, tenantId, normalized);
            case "SUBSCRIPTION.UPDATED" -> updateSubscriptionStatus(data, tenantId);
            case "SUBSCRIPTION.CANCELED", "SUBSCRIPTION.CANCELLED" ->
                    markSubscription(data, tenantId, "CANCELLED", "Suscripcion cancelada en Paddle.");
            case "TRANSACTION.PAYMENT_FAILED", "SUBSCRIPTION.PAST_DUE" ->
                    markSubscription(data, tenantId, "PAST_DUE", "Pago automatico fallido en Paddle.");
            default -> {
                // Evento recibido correctamente, pero no cambia estado operativo.
            }
        }
    }

    private void activateOrRenew(JsonNode data, Long tenantId, String eventType) {
        Subscription subscription = resolveSubscription(data, tenantId);
        if (subscription == null) {
            throw new BusinessException("SUBSCRIPTION_NOT_FOUND", "No se encontro suscripcion local para webhook Paddle.");
        }

        JsonNode custom = data.path("custom_data");
        String requestedPlan = normalizePlan(text(custom, "plan", subscription.getPlan()));
        String billingCycle = normalizeBillingCycle(text(custom, "billingCycle", subscription.getBillingCycle()));
        String currency = cleanUpper(text(custom, "currency", subscription.getCurrency()));
        if (currency.isBlank()) currency = subscription.getCurrency();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endsAt = paddleDate(data.path("current_billing_period").path("ends_at"));
        if (endsAt == null) endsAt = now.plusDays(billingCycleToDays(billingCycle));

        applyPlanConfig(subscription, requestedPlan, pricingService.resolveMonthlyPriceForTenant(subscription.getTenantId(), requestedPlan, currency).doubleValue());
        subscription.setEstado(STATUS_ACTIVE);
        subscription.setTrial(false);
        subscription.setBillingCycle(billingCycle);
        subscription.setCurrency(currency);
        subscription.setFechaInicio(now);
        subscription.setFechaRenovacion(endsAt);
        subscription.setFechaFin(endsAt);
        subscription.setPaddleCustomerId(text(data, "customer_id", subscription.getPaddleCustomerId()));
        subscription.setPaddleSubscriptionId(text(data, "subscription_id", text(data, "id", subscription.getPaddleSubscriptionId())));
        subscription.setPaddleStatus(text(data, "status", "active"));
        subscription.setPaddleLastTransactionId(text(data, "id", subscription.getPaddleLastTransactionId()));
        subscription.setObservaciones("Suscripcion activada/renovada por Paddle: " + eventType);
        subscription.setUpdatedAt(now);
        subscriptionRepository.save(subscription);

        if ("TRANSACTION.COMPLETED".equals(eventType)) {
            recordPaddlePayment(subscription, data, requestedPlan, billingCycle, currency, now);
        }
    }

    private void updateSubscriptionStatus(JsonNode data, Long tenantId) {
        Subscription subscription = resolveSubscription(data, tenantId);
        if (subscription == null) return;

        String paddleStatus = text(data, "status", subscription.getPaddleStatus());
        subscription.setPaddleStatus(paddleStatus);
        if ("active".equalsIgnoreCase(paddleStatus) || "trialing".equalsIgnoreCase(paddleStatus)) {
            subscription.setEstado(STATUS_ACTIVE);
        }

        LocalDateTime endsAt = paddleDate(data.path("current_billing_period").path("ends_at"));
        if (endsAt != null) {
            subscription.setFechaRenovacion(endsAt);
            subscription.setFechaFin(endsAt);
        }

        subscription.setUpdatedAt(LocalDateTime.now());
        subscriptionRepository.save(subscription);
    }

    private void markSubscription(JsonNode data, Long tenantId, String status, String note) {
        Subscription subscription = resolveSubscription(data, tenantId);
        if (subscription == null) return;

        subscription.setEstado(status);
        subscription.setPaddleStatus(text(data, "status", status));
        subscription.setObservaciones(note);
        subscription.setUpdatedAt(LocalDateTime.now());
        subscriptionRepository.save(subscription);
    }

    private void recordPaddlePayment(
            Subscription subscription,
            JsonNode data,
            String requestedPlan,
            String billingCycle,
            String currency,
            LocalDateTime now
    ) {
        String transactionId = text(data, "id");
        if (transactionId.isBlank()) transactionId = text(data, "transaction_id");
        if (transactionId.isBlank()) return;

        if (paymentRepository.findTopByProviderAndProviderPaymentId(PROVIDER, transactionId).isPresent()) {
            return;
        }

        String paymentCurrency = transactionCurrency(data, currency);
        BigDecimal amount = transactionAmount(data, paymentCurrency)
                .orElseGet(() -> BigDecimal.valueOf(
                        pricingService.expectedAmount(requestedPlan, billingCycle, subscription.getTenantId(), currency)
                ));

        SubscriptionPayment payment = SubscriptionPayment.builder()
                .tenantId(subscription.getTenantId())
                .subscriptionId(subscription.getSubId())
                .requestedPlan(requestedPlan)
                .requestedBillingCycle(billingCycle)
                .paymentMethod(PROVIDER)
                .operationNumber(transactionId)
                .provider(PROVIDER)
                .providerPaymentId(transactionId)
                .providerSubscriptionId(subscription.getPaddleSubscriptionId())
                .providerCustomerId(subscription.getPaddleCustomerId())
                .providerCurrency(paymentCurrency)
                .amount(amount)
                .notes("Pago automatico confirmado por Paddle en " + paymentCurrency + ".")
                .status(STATUS_APPROVED)
                .createdAt(now)
                .reviewedAt(now)
                .build();

        paymentRepository.save(payment);
    }

    private java.util.Optional<BigDecimal> transactionAmount(JsonNode data, String currency) {
        JsonNode totals = data.path("details").path("totals");
        String raw = firstText(
                totals.path("grand_total"),
                totals.path("total"),
                data.path("payments").isArray() && !data.path("payments").isEmpty()
                        ? data.path("payments").get(0).path("amount")
                        : null
        );

        if (raw.isBlank()) return java.util.Optional.empty();

        try {
            BigDecimal value = new BigDecimal(raw.trim());
            if (raw.contains(".") || raw.contains(",")) {
                return java.util.Optional.of(value.setScale(2, RoundingMode.HALF_UP));
            }

            int scale = currencyScale(currency);
            if (scale <= 0) {
                return java.util.Optional.of(value.setScale(2, RoundingMode.HALF_UP));
            }

            return java.util.Optional.of(value
                    .movePointLeft(scale)
                    .setScale(2, RoundingMode.HALF_UP));
        } catch (NumberFormatException ignored) {
            return java.util.Optional.empty();
        }
    }

    private String transactionCurrency(JsonNode data, String fallback) {
        String value = cleanUpper(firstText(
                data.path("currency_code"),
                data.path("details").path("totals").path("currency_code"),
                data.path("payments").isArray() && !data.path("payments").isEmpty()
                        ? data.path("payments").get(0).path("currency_code")
                        : null
        ));
        return value.isBlank() ? cleanUpper(fallback) : value;
    }

    private String firstText(JsonNode... nodes) {
        if (nodes == null) return "";
        for (JsonNode node : nodes) {
            if (node == null || node.isMissingNode() || node.isNull()) continue;
            String value = node.asText("");
            if (value != null && !value.isBlank()) return value.trim().replace(",", ".");
        }
        return "";
    }

    private int currencyScale(String currency) {
        String value = cleanUpper(currency);
        if (ZERO_DECIMAL_CURRENCIES.contains(value)) return 0;
        return 2;
    }

    private Subscription resolveSubscription(JsonNode data, Long tenantId) {
        if (tenantId != null) {
            return subscriptionRepository.findTopByTenantIdOrderBySubIdDesc(tenantId).orElse(null);
        }

        String subscriptionId = text(data, "subscription_id");
        if (subscriptionId.isBlank()) subscriptionId = text(data, "id");
        if (subscriptionId.isBlank()) return null;

        return subscriptionRepository.findTopByPaddleSubscriptionIdOrderBySubIdDesc(subscriptionId).orElse(null);
    }

    private Long tenantIdFrom(JsonNode data) {
        JsonNode custom = data.path("custom_data");
        String raw = text(custom, "tenantId");
        if (raw.isBlank()) raw = text(custom, "tenant_id");
        if (raw.isBlank()) return null;

        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void verifySignature(String rawBody, String signatureHeader) {
        String secret = webhookSecret();
        if (secret.isBlank()) {
            throw new BusinessException("PADDLE_WEBHOOK_SECRET_MISSING", "Falta configurar el secret del webhook Paddle.");
        }

        String ts = "";
        String h1 = "";
        for (String part : String.valueOf(signatureHeader).split(";")) {
            String[] pieces = part.split("=", 2);
            if (pieces.length != 2) continue;
            if ("ts".equals(pieces[0])) ts = pieces[1];
            if ("h1".equals(pieces[0])) h1 = pieces[1];
        }

        if (ts.isBlank() || h1.isBlank()) {
            throw new BusinessException("PADDLE_SIGNATURE_INVALID", "Firma Paddle invalida.");
        }

        String signedPayload = ts + ":" + rawBody;
        String expected = hmacSha256Hex(secret, signedPayload);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), h1.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException("PADDLE_SIGNATURE_INVALID", "Firma Paddle no coincide.");
        }
    }

    private String webhookSecret() {
        String value = environment.getProperty("billing.paddle.webhook.secret", "");
        if (!value.isBlank()) return value.trim();

        value = environment.getProperty("paddle.webhook.secret", "");
        if (!value.isBlank()) return value.trim();

        return environment.getProperty("PADDLE_WEBHOOK_SECRET", "").trim();
    }

    private String hmacSha256Hex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new BusinessException("PADDLE_SIGNATURE_ERROR", "No se pudo validar la firma Paddle.");
        }
    }

    private JsonNode readJson(String rawBody) {
        try {
            return objectMapper.readTree(rawBody);
        } catch (Exception e) {
            throw new BusinessException("PADDLE_WEBHOOK_INVALID", "JSON invalido en webhook Paddle.");
        }
    }

    private LocalDateTime paddleDate(JsonNode node) {
        String value = node == null ? "" : node.asText("");
        if (value.isBlank()) return null;

        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String text(JsonNode node, String field) {
        return text(node, field, "");
    }

    private String text(JsonNode node, String field, String fallback) {
        if (node == null || node.isMissingNode() || node.path(field).isMissingNode() || node.path(field).isNull()) {
            return fallback == null ? "" : fallback;
        }
        String value = node.path(field).asText("");
        return value == null || value.isBlank() ? (fallback == null ? "" : fallback) : value.trim();
    }

    private String cleanUpper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizePlan(String plan) {
        return SubscriptionPlanCatalog.publicPlan(plan);
    }

    private String normalizeBillingCycle(String billingCycle) {
        String value = cleanUpper(billingCycle);
        return switch (value) {
            case "SEMIANNUAL", "ANNUAL" -> value;
            default -> "MONTHLY";
        };
    }

    private int billingCycleToDays(String billingCycle) {
        return switch (normalizeBillingCycle(billingCycle)) {
            case "SEMIANNUAL" -> 180;
            case "ANNUAL" -> 365;
            default -> 30;
        };
    }

    private void applyPlanConfig(Subscription sub, String normalizedPlan, double monthlyPrice) {
        SubscriptionPlanCatalog.applyTo(sub, normalizedPlan, monthlyPrice);
    }
}

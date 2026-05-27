package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.ReportPaymentRequest;
import com.gods.saas.domain.dto.request.SubscriptionCheckoutRequest;
import com.gods.saas.domain.dto.response.SubscriptionCheckoutResponse;
import com.gods.saas.domain.dto.response.SubscriptionCurrentResponse;
import com.gods.saas.domain.dto.response.SubscriptionPlanPriceResponse;
import com.gods.saas.domain.model.Subscription;
import com.gods.saas.domain.model.SubscriptionPayment;
import com.gods.saas.domain.model.Tenant;
import com.gods.saas.domain.model.TenantSettings;
import com.gods.saas.domain.repository.*;
import com.gods.saas.exception.BusinessException;
import com.gods.saas.service.impl.impl.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    private static final String DEFAULT_TIMEZONE = "America/Lima";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_TRIAL = "TRIAL";
    private static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";

    private final TenantSettingsRepository tenantSettingsRepository;
    private final TenantRepository tenantRepository;
    private final SubscriptionPlanPricingService pricingService;
    private final SuscriptionRepository subscriptionRepository;
    private final SubscriptionPaymentRepository paymentRepository;
    private final BranchRepository branchRepository;
    private final AppUserRepository appUserRepository;
    private final Environment environment;

    @Override
    public Subscription getCurrentSubscriptionOrThrow(Long tenantId) {
        return subscriptionRepository.findTopByTenantIdOrderBySubIdDesc(tenantId)
                .orElseThrow(() -> new BusinessException(
                        "SUBSCRIPTION_NOT_FOUND",
                        "No existe suscripciÃ³n para este tenant"
                ));
    }

    @Override
    public void validateSubscriptionActive(Long tenantId) {
        Subscription sub = getCurrentSubscriptionOrThrow(tenantId);
        validateUsableOrThrow(sub);
    }

    @Override
    public void validateBranchLimit(Long tenantId) {
        Subscription sub = getCurrentSubscriptionOrThrow(tenantId);
        validateUsableOrThrow(sub);

        long currentBranches = branchRepository.countByTenant_Id(tenantId);
        Integer maxBranches = sub.getMaxBranches();

        if (maxBranches != null && currentBranches >= maxBranches) {
            throw new BusinessException(
                    "PLAN_LIMIT_BRANCHES",
                    "Tu plan actual no permite crear mÃ¡s sedes"
            );
        }
    }

    @Override
    public void validateBarberLimit(Long tenantId) {
        Subscription sub = getCurrentSubscriptionOrThrow(tenantId);
        validateUsableOrThrow(sub);

        long currentBarbers = appUserRepository.countByTenantIdAndRolIgnoreCaseAndActivoTrue(
                tenantId, "BARBER"
        );
        Integer maxBarbers = sub.getMaxBarbers();

        if (maxBarbers != null && currentBarbers >= maxBarbers) {
            throw new BusinessException(
                    "PLAN_LIMIT_BARBERS",
                    "Tu plan actual no permite crear mÃ¡s barberos"
            );
        }
    }

    @Override
    public void validateAdminLimit(Long tenantId) {
        Subscription sub = getCurrentSubscriptionOrThrow(tenantId);
        validateUsableOrThrow(sub);

        long currentAdmins = appUserRepository.countActiveByTenantIdAndRoles(
                tenantId,
                List.of("OWNER", "ADMIN")
        );
        Integer maxAdmins = sub.getMaxAdmins();

        if (maxAdmins != null && currentAdmins >= maxAdmins) {
            throw new BusinessException(
                    "PLAN_LIMIT_ADMINS",
                    "Tu plan actual no permite crear mÃ¡s administradores"
            );
        }
    }

    @Override
    public Subscription createStarterTrial(Long tenantId) {
        LocalDateTime now = nowForTenant(tenantId);

        Subscription subscription = Subscription.builder()
                .tenantId(tenantId)
                .plan("STARTER")
                .precioMensual(pricingService.resolveMonthlyPriceForTenant(tenantId, "STARTER", null).doubleValue())
                .estado(STATUS_TRIAL)
                .fechaInicio(now)
                .fechaRenovacion(now.plusDays(7))
                .fechaFin(now.plusDays(7))
                .trial(true)
                .diasGracia(0)
                .maxBranches(1)
                .maxBarbers(5)
                .maxAdmins(1)
                .aiEnabled(false)
                .loyaltyEnabled(true)
                .promotionsEnabled(true)
                .billingCycle("MONTHLY")
                .currency(resolveCurrencyForTenant(tenantId, "PEN"))
                .observaciones("Trial inicial automÃ¡tico")
                .createdAt(now)
                .updatedAt(now)
                .build();

        return subscriptionRepository.save(subscription);
    }

    private String resolveCurrencyForTenant(Long tenantId, String fallback) {
        Tenant tenant = tenantId == null ? null : tenantRepository.findById(tenantId).orElse(null);
        return pricingService.resolveTenantCurrency(tenantId, tenant, fallback);
    }

    private ZoneId getZoneIdForTenant(Long tenantId) {
        String timezone = tenantSettingsRepository.findByTenant_Id(tenantId)
                .map(TenantSettings::getTimezone)
                .filter(tz -> tz != null && !tz.isBlank())
                .orElse(DEFAULT_TIMEZONE);

        try {
            return ZoneId.of(timezone);
        } catch (Exception e) {
            return ZoneId.of(DEFAULT_TIMEZONE);
        }
    }

    private LocalDateTime nowForTenant(Long tenantId) {
        return LocalDateTime.now(getZoneIdForTenant(tenantId));
    }

    @Override
    public Subscription changePlan(Long tenantId, String plan) {
        Subscription sub = getCurrentSubscriptionOrThrow(tenantId);

        String normalizedPlan = normalizePlan(plan);
        LocalDateTime now = nowForTenant(tenantId);

        applyPlanConfig(sub, normalizedPlan, pricingService.resolveMonthlyPriceForTenant(tenantId, normalizedPlan, sub.getCurrency()).doubleValue());

        sub.setEstado(STATUS_ACTIVE);
        sub.setTrial(false);
        sub.setBillingCycle("MONTHLY");
        sub.setCurrency(resolveCurrencyForTenant(tenantId, sub.getCurrency()));
        sub.setFechaInicio(now);
        sub.setFechaRenovacion(now.plusDays(30));
        sub.setFechaFin(now.plusDays(30));
        sub.setUpdatedAt(now);
        sub.setObservaciones("Plan actualizado manualmente a " + normalizedPlan);

        return subscriptionRepository.save(sub);
    }

    @Override
    public SubscriptionCurrentResponse getCurrentSubscriptionResponse(Long tenantId) {
        Subscription sub = getCurrentSubscriptionOrThrow(tenantId);

        long usedBranches = branchRepository.countByTenant_Id(tenantId);
        long usedBarbers = appUserRepository.countByTenantIdAndRolIgnoreCaseAndActivoTrue(tenantId, "BARBER");
        long usedAdmins = appUserRepository.countActiveByTenantIdAndRoles(
                tenantId,
                List.of("OWNER", "ADMIN")
        );

        boolean usable = isSubscriptionUsable(sub);
        boolean expired = isExpired(sub);

        return SubscriptionCurrentResponse.builder()
                .subId(sub.getSubId())
                .tenantId(sub.getTenantId())
                .plan(sub.getPlan())
                .estado(sub.getEstado())
                .trial(sub.isTrial())
                .precioMensual(sub.getPrecioMensual())
                .billingCycle(sub.getBillingCycle())
                .currency(sub.getCurrency())
                .planPrices(pricingService.listMonthlyPricesForTenant(tenantId))
                .fechaInicio(sub.getFechaInicio())
                .fechaRenovacion(sub.getFechaRenovacion())
                .fechaFin(sub.getFechaFin())
                .diasGracia(sub.getDiasGracia())
                .observaciones(sub.getObservaciones())
                .maxBranches(sub.getMaxBranches())
                .usedBranches(Math.toIntExact(usedBranches))
                .maxBarbers(sub.getMaxBarbers())
                .usedBarbers(Math.toIntExact(usedBarbers))
                .maxAdmins(sub.getMaxAdmins())
                .usedAdmins(Math.toIntExact(usedAdmins))
                .aiEnabled(sub.isAiEnabled())
                .loyaltyEnabled(sub.isLoyaltyEnabled())
                .promotionsEnabled(sub.isPromotionsEnabled())
                .canOperate(usable)
                .expired(expired)
                .build();
    }

    @Override
    public List<SubscriptionPlanPriceResponse> getPlanPrices(Long tenantId) {
        return pricingService.listMonthlyPricesForTenant(tenantId);
    }

    @Override
    public SubscriptionCheckoutResponse createInternationalCheckout(Long tenantId, SubscriptionCheckoutRequest request) {
        Subscription sub = getCurrentSubscriptionOrThrow(tenantId);

        String requestedPlan = normalizePlan(request != null ? request.getPlan() : sub.getPlan());
        String billingCycle = normalizeBillingCycle(request != null ? request.getBillingCycle() : sub.getBillingCycle());
        String currency = resolveCurrencyForTenant(tenantId, sub.getCurrency());
        double amount = calculateExpectedAmount(requestedPlan, billingCycle, tenantId, currency);

        // Permitimos checkout con Paddle tambien para Peru/PEN.
        // El cliente puede elegir pago manual por Yape o pago automatico con tarjeta.
        // Si Paddle no tiene configurado el priceId correcto para el entorno,
        // se controlara abajo con PADDLE_NOT_CONFIGURED.

        if (!"MONTHLY".equalsIgnoreCase(billingCycle)) {
            throw new BusinessException(
                    "PADDLE_BILLING_CYCLE_NOT_CONFIGURED",
                    "Por ahora el pago automatico internacional esta disponible solo en ciclo mensual."
            );
        }

        String priceId = resolvePaddlePriceId(requestedPlan, billingCycle, currency);
        if (priceId.isBlank()) {
            throw new BusinessException(
                    "PADDLE_NOT_CONFIGURED",
                    "No existe priceId de Paddle configurado para el plan " + requestedPlan +
                            ", ciclo " + billingCycle + " y moneda " + currency +
                            ". Revisa la variable BILLING_PADDLE_PRICE_" +
                            requestedPlan.replace("_", "") + "_" + billingCycle + "_" + currency
            );
        }

        return SubscriptionCheckoutResponse.builder()
                .provider("PADDLE")
                .checkoutUrl(null)
                .priceId(priceId)
                .currency(currency)
                .amount(amount)
                .build();
    }

    private String resolvePaddlePriceId(String plan, String billingCycle, String currency) {
        String planKey = normalizePlan(plan).toLowerCase(Locale.ROOT).replace("_", "");
        String cycleKey = normalizeBillingCycle(billingCycle).toLowerCase(Locale.ROOT);
        String currencyKey = normalizeText(currency).toLowerCase(Locale.ROOT);

        // Primero buscamos por moneda para evitar que Peru/PEN use por error el priceId USD.
        // Railway variables esperadas:
        // BILLING_PADDLE_PRICE_STARTER_MONTHLY_PEN
        // BILLING_PADDLE_PRICE_PRO_MONTHLY_PEN
        // BILLING_PADDLE_PRICE_GODSAI_MONTHLY_PEN
        String specificCurrencyKey = "billing.paddle.price." + planKey + "." + cycleKey + "." + currencyKey;
        String currencySpecific = readProperty(specificCurrencyKey);
        if (!currencySpecific.isBlank()) return currencySpecific;

        // Seguridad: si la moneda es PEN, NO hacemos fallback a las variables antiguas sin moneda,
        // porque esas variables apuntan a USD y Paddle lo convierte a soles.
        if ("pen".equals(currencyKey)) {
            return "";
        }

        // Compatibilidad para internacional/USD con tus variables antiguas.
        String specificKey = "billing.paddle.price." + planKey + "." + cycleKey;
        String specific = readProperty(specificKey);
        if (!specific.isBlank()) return specific;

        String planKeyOnly = "billing.paddle.price." + planKey;
        String planPrice = readProperty(planKeyOnly);
        if (!planPrice.isBlank()) return planPrice;

        return switch (planKey + "." + cycleKey + "." + currencyKey) {
            case "starter.monthly.usd" -> "pri_01ksge6ezebjjhq4gt887fnqt1";
            case "pro.monthly.usd" -> "pri_01ksgeahph8s0j59vm4ba2g1t2";
            case "godsai.monthly.usd" -> "pri_01ksgdxcatyps3scp8eqpnhv7n";
            default -> "";
        };
    }

    private String readProperty(String propertyKey) {
        String value = environment.getProperty(propertyKey, "");
        if (value != null && !value.trim().isBlank()) return value.trim();

        // Lectura explicita para Railway/env vars, por si el mapeo relaxed no resuelve dots a underscores.
        String envKey = propertyKey.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_');
        value = environment.getProperty(envKey, "");
        if (value != null && !value.trim().isBlank()) return value.trim();

        value = System.getenv(envKey);
        return value == null ? "" : value.trim();
    }
    @Override
    public SubscriptionPayment reportManualPayment(Long tenantId, ReportPaymentRequest request) {
        Subscription sub = getCurrentSubscriptionOrThrow(tenantId);

        paymentRepository.findTopByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, STATUS_PENDING_REVIEW)
                .ifPresent(p -> {
                    throw new BusinessException(
                            "PAYMENT_ALREADY_PENDING",
                            "Ya existe un pago pendiente de revisiÃ³n para este tenant"
                    );
                });

        String requestedPlan = normalizePlan(request.getPlan());
        String billingCycle = normalizeBillingCycle(request.getBillingCycle());

        validateAmount(request.getAmount());

        double expectedAmount = calculateExpectedAmount(requestedPlan, billingCycle, tenantId, sub.getCurrency());
        validateReportedAmount(request.getAmount(), expectedAmount);

        LocalDateTime now = nowForTenant(tenantId);

        SubscriptionPayment payment = SubscriptionPayment.builder()
                .tenantId(tenantId)
                .subscriptionId(sub.getSubId())
                .requestedPlan(requestedPlan)
                .requestedBillingCycle(billingCycle)
                .paymentMethod(normalizePaymentMethod(request.getPaymentMethod()))
                .operationNumber(requiredText(request.getOperationNumber(), "NÃºmero de operaciÃ³n obligatorio"))
                .amount(BigDecimal.valueOf(request.getAmount()))
                .payerName(trimToNull(request.getPayerName()))
                .payerPhone(trimToNull(request.getPayerPhone()))
                .notes(trimToNull(request.getNotes()))
                .status(STATUS_PENDING_REVIEW)
                .createdAt(now)
                .build();

        sub.setUpdatedAt(now);
        sub.setObservaciones("Pago reportado manualmente pendiente de revisiÃ³n. OperaciÃ³n: " + request.getOperationNumber());
        subscriptionRepository.save(sub);

        return paymentRepository.save(payment);
    }

    @Override
    public Subscription approveManualPayment(Long paymentId, Long reviewedByUserId) {
        SubscriptionPayment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(
                        "PAYMENT_NOT_FOUND",
                        "No se encontrÃ³ el pago"
                ));

        if (!STATUS_PENDING_REVIEW.equalsIgnoreCase(payment.getStatus())) {
            throw new BusinessException(
                    "PAYMENT_ALREADY_REVIEWED",
                    "Este pago ya fue revisado"
            );
        }

        Subscription sub = getCurrentSubscriptionOrThrow(payment.getTenantId());
        LocalDateTime now = nowForTenant(payment.getTenantId());

        applyPlanConfig(sub, payment.getRequestedPlan(), pricingService.resolveMonthlyPriceForTenant(payment.getTenantId(), payment.getRequestedPlan(), sub.getCurrency()).doubleValue());

        int days = billingCycleToDays(payment.getRequestedBillingCycle());

        sub.setEstado(STATUS_ACTIVE);
        sub.setTrial(false);
        sub.setBillingCycle(normalizeBillingCycle(payment.getRequestedBillingCycle()));
        sub.setCurrency(resolveCurrencyForTenant(payment.getTenantId(), sub.getCurrency()));
        sub.setFechaInicio(now);
        sub.setFechaRenovacion(now.plusDays(days));
        sub.setFechaFin(now.plusDays(days));
        sub.setUpdatedAt(now);
        sub.setObservaciones("Pago aprobado manualmente. Plan: " + payment.getRequestedPlan());

        payment.setStatus(STATUS_APPROVED);
        payment.setReviewedAt(now);
        payment.setReviewedByUserId(reviewedByUserId);

        paymentRepository.save(payment);
        return subscriptionRepository.save(sub);
    }

    @Override
    public SubscriptionPayment rejectManualPayment(Long paymentId, Long reviewedByUserId, String reason) {
        SubscriptionPayment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(
                        "PAYMENT_NOT_FOUND",
                        "No se encontrÃ³ el pago"
                ));

        if (!STATUS_PENDING_REVIEW.equalsIgnoreCase(payment.getStatus())) {
            throw new BusinessException(
                    "PAYMENT_ALREADY_REVIEWED",
                    "Este pago ya fue revisado"
            );
        }

        Subscription sub = getCurrentSubscriptionOrThrow(payment.getTenantId());
        LocalDateTime now = nowForTenant(payment.getTenantId());

        payment.setStatus(STATUS_REJECTED);
        payment.setReviewedAt(now);
        payment.setReviewedByUserId(reviewedByUserId);

        String note = trimToNull(reason);
        if (note != null) {
            payment.setNotes(
                    payment.getNotes() == null
                            ? "RECHAZADO: " + note
                            : payment.getNotes() + " | RECHAZADO: " + note
            );
        }

        sub.setEstado(isExpired(sub) ? "EXPIRED" : STATUS_ACTIVE);
        sub.setUpdatedAt(now);
        sub.setObservaciones("Pago rechazado manualmente. OperaciÃ³n: " + payment.getOperationNumber());

        subscriptionRepository.save(sub);
        return paymentRepository.save(payment);
    }

    private void validateUsableOrThrow(Subscription sub) {
        String estado = normalizeText(sub.getEstado());

        if (!STATUS_ACTIVE.equals(estado) && !STATUS_TRIAL.equals(estado)) {
            throw new BusinessException(
                    "SUBSCRIPTION_INACTIVE",
                    "Tu suscripciÃ³n no estÃ¡ activa"
            );
        }

        if (isExpired(sub)) {
            throw new BusinessException(
                    "SUBSCRIPTION_EXPIRED",
                    "Tu licencia estÃ¡ vencida o inactiva"
            );
        }
    }

    private boolean isSubscriptionUsable(Subscription subscription) {
        if (subscription == null) return false;

        String estado = normalizeText(subscription.getEstado());

        if (!STATUS_ACTIVE.equals(estado) && !STATUS_TRIAL.equals(estado)) {
            return false;
        }

        if (subscription.getFechaFin() == null) {
            return false;
        }

        int diasGracia = subscription.getDiasGracia() == null ? 0 : subscription.getDiasGracia();
        return nowForTenant(subscription.getTenantId())
                .isBefore(subscription.getFechaFin().plusDays(diasGracia));
    }

    private boolean isExpired(Subscription subscription) {
        if (subscription == null || subscription.getFechaFin() == null) return true;
        int diasGracia = subscription.getDiasGracia() == null ? 0 : subscription.getDiasGracia();
        return !nowForTenant(subscription.getTenantId())
                .isBefore(subscription.getFechaFin().plusDays(diasGracia));
    }

    private String normalizePlan(String plan) {
        String normalizedPlan = normalizeText(plan);
        return switch (normalizedPlan) {
            case "STARTER", "PRO", "GODS_AI" -> normalizedPlan;
            default -> throw new BusinessException(
                    "PLAN_INVALID",
                    "Plan no vÃ¡lido. Usa STARTER, PRO o GODS_AI"
            );
        };
    }

    private String normalizeBillingCycle(String billingCycle) {
        String value = normalizeText(billingCycle);
        if (value.isBlank()) return "MONTHLY";

        return switch (value) {
            case "MONTHLY", "SEMIANNUAL", "ANNUAL" -> value;
            default -> "MONTHLY";
        };
    }

    private String normalizePaymentMethod(String paymentMethod) {
        String value = normalizeText(paymentMethod);
        return value.isBlank() ? "YAPE" : value;
    }

    private void validateAmount(Double amount) {
        if (amount == null || amount <= 0) {
            throw new BusinessException(
                    "INVALID_AMOUNT",
                    "El monto es invÃ¡lido"
            );
        }
    }

    private void validateReportedAmount(Double reportedAmount, double expectedAmount) {
        double tolerance = 0.01;
        if (Math.abs(reportedAmount - expectedAmount) > tolerance) {
            throw new BusinessException(
                    "INVALID_PAYMENT_AMOUNT",
                    "El monto reportado no coincide con el monto esperado para el plan y ciclo seleccionado"
            );
        }
    }

    private double calculateExpectedAmount(String plan, String billingCycle, Long tenantId, String preferredCurrency) {
        return pricingService.expectedAmount(plan, billingCycle, tenantId, preferredCurrency);
    }

    private int billingCycleToDays(String billingCycle) {
        return switch (normalizeBillingCycle(billingCycle)) {
            case "MONTHLY" -> 30;
            case "SEMIANNUAL" -> 180;
            case "ANNUAL" -> 365;
            default -> 30;
        };
    }

    private void applyPlanConfig(Subscription sub, String normalizedPlan, double monthlyPrice) {
        switch (normalizedPlan) {
            case "STARTER" -> {
                sub.setPlan("STARTER");
                sub.setPrecioMensual(monthlyPrice);
                sub.setMaxBranches(1);
                sub.setMaxBarbers(5);
                sub.setMaxAdmins(1);
                sub.setAiEnabled(false);
                sub.setLoyaltyEnabled(true);
                sub.setPromotionsEnabled(true);
            }
            case "PRO" -> {
                sub.setPlan("PRO");
                sub.setPrecioMensual(monthlyPrice);
                sub.setMaxBranches(3);
                sub.setMaxBarbers(15);
                sub.setMaxAdmins(3);
                sub.setAiEnabled(false);
                sub.setLoyaltyEnabled(true);
                sub.setPromotionsEnabled(true);
            }
            case "GODS_AI" -> {
                sub.setPlan("GODS_AI");
                sub.setPrecioMensual(monthlyPrice);
                sub.setMaxBranches(10);
                sub.setMaxBarbers(50);
                sub.setMaxAdmins(10);
                sub.setAiEnabled(true);
                sub.setLoyaltyEnabled(true);
                sub.setPromotionsEnabled(true);
            }
            default -> throw new BusinessException(
                    "PLAN_INVALID",
                    "Plan no vÃ¡lido. Usa STARTER, PRO o GODS_AI"
            );
        }
    }

    private String requiredText(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BusinessException("VALIDATION_ERROR", message);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}



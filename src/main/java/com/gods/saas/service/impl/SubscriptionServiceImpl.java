package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.ReportPaymentRequest;
import com.gods.saas.domain.dto.response.SubscriptionCurrentResponse;
import com.gods.saas.domain.model.Subscription;
import com.gods.saas.domain.model.SubscriptionPayment;
import com.gods.saas.domain.repository.AppUserRepository;
import com.gods.saas.domain.repository.BranchRepository;
import com.gods.saas.domain.repository.SubscriptionPaymentRepository;
import com.gods.saas.domain.repository.SuscriptionRepository;
import com.gods.saas.exception.BusinessException;
import com.gods.saas.service.impl.impl.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_TRIAL = "TRIAL";
    private static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";

    private final SuscriptionRepository subscriptionRepository;
    private final SubscriptionPaymentRepository paymentRepository;
    private final BranchRepository branchRepository;
    private final AppUserRepository appUserRepository;

    @Override
    public Subscription getCurrentSubscriptionOrThrow(Long tenantId) {
        return subscriptionRepository.findTopByTenantIdOrderBySubIdDesc(tenantId)
                .orElseThrow(() -> new BusinessException(
                        "SUBSCRIPTION_NOT_FOUND",
                        "No existe suscripción para este tenant"
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
                    "Tu plan actual no permite crear más sedes"
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
                    "Tu plan actual no permite crear más barberos"
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
                    "Tu plan actual no permite crear más administradores"
            );
        }
    }

    @Override
    public Subscription createStarterTrial(Long tenantId) {
        LocalDateTime now = LocalDateTime.now();

        Subscription subscription = Subscription.builder()
                .tenantId(tenantId)
                .plan("STARTER")
                .precioMensual(9.0)
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
                .currency("USD")
                .observaciones("Trial inicial automático")
                .createdAt(now)
                .updatedAt(now)
                .build();

        return subscriptionRepository.save(subscription);
    }

    @Override
    public Subscription changePlan(Long tenantId, String plan) {
        Subscription sub = getCurrentSubscriptionOrThrow(tenantId);

        String normalizedPlan = normalizePlan(plan);
        LocalDateTime now = LocalDateTime.now();

        applyPlanConfig(sub, normalizedPlan);

        sub.setEstado(STATUS_ACTIVE);
        sub.setTrial(false);
        sub.setBillingCycle("MONTHLY");
        sub.setCurrency("USD");
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
    public SubscriptionPayment reportManualPayment(Long tenantId, ReportPaymentRequest request) {
        Subscription sub = getCurrentSubscriptionOrThrow(tenantId);

        paymentRepository.findTopByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, "PENDING_REVIEW")
                .ifPresent(p -> {
                    throw new BusinessException(
                            "PAYMENT_ALREADY_PENDING",
                            "Ya existe un pago pendiente de revisión para este tenant"
                    );
                });

        String requestedPlan = normalizePlan(request.getPlan());
        String billingCycle = normalizeBillingCycle(request.getBillingCycle());

        validateAmount(request.getAmount());

        double expectedAmount = calculateExpectedAmount(requestedPlan, billingCycle);
        validateReportedAmount(request.getAmount(), expectedAmount);

        SubscriptionPayment payment = SubscriptionPayment.builder()
                .tenantId(tenantId)
                .subscriptionId(sub.getSubId())
                .requestedPlan(requestedPlan)
                .requestedBillingCycle(billingCycle)
                .paymentMethod(normalizePaymentMethod(request.getPaymentMethod()))
                .operationNumber(requiredText(request.getOperationNumber(), "Número de operación obligatorio"))
                .amount(BigDecimal.valueOf(request.getAmount()))
                .payerName(trimToNull(request.getPayerName()))
                .payerPhone(trimToNull(request.getPayerPhone()))
                .notes(trimToNull(request.getNotes()))
                .status("PENDING_REVIEW")
                .createdAt(LocalDateTime.now())
                .build();

        sub.setUpdatedAt(LocalDateTime.now());
        sub.setObservaciones("Pago reportado manualmente pendiente de revisión. Operación: " + request.getOperationNumber());
        subscriptionRepository.save(sub);

        return paymentRepository.save(payment);
    }

    @Override
    public Subscription approveManualPayment(Long paymentId, Long reviewedByUserId) {
        SubscriptionPayment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(
                        "PAYMENT_NOT_FOUND",
                        "No se encontró el pago"
                ));

        if (!STATUS_PENDING_REVIEW.equalsIgnoreCase(payment.getStatus())) {
            throw new BusinessException(
                    "PAYMENT_ALREADY_REVIEWED",
                    "Este pago ya fue revisado"
            );
        }

        Subscription sub = getCurrentSubscriptionOrThrow(payment.getTenantId());
        LocalDateTime now = LocalDateTime.now();

        applyPlanConfig(sub, payment.getRequestedPlan());

        int days = billingCycleToDays(payment.getRequestedBillingCycle());

        sub.setEstado(STATUS_ACTIVE);
        sub.setTrial(false);
        sub.setBillingCycle(normalizeBillingCycle(payment.getRequestedBillingCycle()));
        sub.setCurrency("USD");
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
                        "No se encontró el pago"
                ));

        if (!STATUS_PENDING_REVIEW.equalsIgnoreCase(payment.getStatus())) {
            throw new BusinessException(
                    "PAYMENT_ALREADY_REVIEWED",
                    "Este pago ya fue revisado"
            );
        }

        Subscription sub = getCurrentSubscriptionOrThrow(payment.getTenantId());

        payment.setStatus(STATUS_REJECTED);
        payment.setReviewedAt(LocalDateTime.now());
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
        sub.setUpdatedAt(LocalDateTime.now());
        sub.setObservaciones("Pago rechazado manualmente. Operación: " + payment.getOperationNumber());

        subscriptionRepository.save(sub);
        return paymentRepository.save(payment);
    }

    private void validateUsableOrThrow(Subscription sub) {
        String estado = normalizeText(sub.getEstado());

        if (!STATUS_ACTIVE.equals(estado) && !STATUS_TRIAL.equals(estado)) {
            throw new BusinessException(
                    "SUBSCRIPTION_INACTIVE",
                    "Tu suscripción no está activa"
            );
        }

        if (isExpired(sub)) {
            throw new BusinessException(
                    "SUBSCRIPTION_EXPIRED",
                    "Tu licencia está vencida o inactiva"
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
        return LocalDateTime.now().isBefore(subscription.getFechaFin().plusDays(diasGracia));
    }

    private boolean isExpired(Subscription subscription) {
        if (subscription == null || subscription.getFechaFin() == null) return true;
        int diasGracia = subscription.getDiasGracia() == null ? 0 : subscription.getDiasGracia();
        return !LocalDateTime.now().isBefore(subscription.getFechaFin().plusDays(diasGracia));
    }

    private String normalizePlan(String plan) {
        String normalizedPlan = normalizeText(plan);
        return switch (normalizedPlan) {
            case "STARTER", "PRO", "GODS_AI" -> normalizedPlan;
            default -> throw new BusinessException(
                    "PLAN_INVALID",
                    "Plan no válido. Usa STARTER, PRO o GODS_AI"
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
                    "El monto es inválido"
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

    private double calculateExpectedAmount(String plan, String billingCycle) {
        double monthly = monthlyPrice(plan);
        double base = switch (billingCycle) {
            case "MONTHLY" -> monthly;
            case "SEMIANNUAL" -> monthly * 6;
            case "ANNUAL" -> monthly * 12;
            default -> monthly;
        };

        double discount = switch (billingCycle) {
            case "SEMIANNUAL" -> 0.10;
            case "ANNUAL" -> 0.20;
            default -> 0.0;
        };

        return round2(base * (1 - discount));
    }

    private double monthlyPrice(String plan) {
        return switch (plan) {
            case "STARTER" -> 35.0;
            case "PRO" -> 75.0;
            case "GODS_AI" -> 149.0;
            default -> throw new BusinessException("PLAN_INVALID", "Plan no válido");
        };
    }

    private int billingCycleToDays(String billingCycle) {
        return switch (normalizeBillingCycle(billingCycle)) {
            case "MONTHLY" -> 30;
            case "SEMIANNUAL" -> 180;
            case "ANNUAL" -> 365;
            default -> 30;
        };
    }

    private void applyPlanConfig(Subscription sub, String normalizedPlan) {
        switch (normalizedPlan) {
            case "STARTER" -> {
                sub.setPlan("STARTER");
                sub.setPrecioMensual(35.0);
                sub.setMaxBranches(1);
                sub.setMaxBarbers(5);
                sub.setMaxAdmins(1);
                sub.setAiEnabled(false);
                sub.setLoyaltyEnabled(true);
                sub.setPromotionsEnabled(true);
            }
            case "PRO" -> {
                sub.setPlan("PRO");
                sub.setPrecioMensual(75.0);
                sub.setMaxBranches(3);
                sub.setMaxBarbers(15);
                sub.setMaxAdmins(3);
                sub.setAiEnabled(false);
                sub.setLoyaltyEnabled(true);
                sub.setPromotionsEnabled(true);
            }
            case "GODS_AI" -> {
                sub.setPlan("GODS_AI");
                sub.setPrecioMensual(149.0);
                sub.setMaxBranches(10);
                sub.setMaxBarbers(50);
                sub.setMaxAdmins(10);
                sub.setAiEnabled(true);
                sub.setLoyaltyEnabled(true);
                sub.setPromotionsEnabled(true);
            }
            default -> throw new BusinessException(
                    "PLAN_INVALID",
                    "Plan no válido. Usa STARTER, PRO o GODS_AI"
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
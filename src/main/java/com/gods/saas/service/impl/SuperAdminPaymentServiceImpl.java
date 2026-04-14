package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.ApprovesPaymentRequest;
import com.gods.saas.domain.dto.request.RejectPaymentRequest;
import com.gods.saas.domain.dto.response.SuperAdminPaymentResponse;
import com.gods.saas.domain.model.Subscription;
import com.gods.saas.domain.model.SubscriptionPayment;
import com.gods.saas.domain.model.Tenant;
import com.gods.saas.domain.repository.SubscriptionPaymentRepository;
import com.gods.saas.domain.repository.SubscriptionRepository;
import com.gods.saas.domain.repository.TenantRepository;
import com.gods.saas.service.impl.impl.SuperAdminPaymentService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class SuperAdminPaymentServiceImpl implements SuperAdminPaymentService {

    private static final String DEFAULT_TIMEZONE = "America/Lima";

    private final SubscriptionPaymentRepository paymentRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TenantRepository tenantRepository;

    @Override
    @Transactional(readOnly = true)
    public List<SuperAdminPaymentResponse> findPending() {
        return paymentRepository.findByStatusOrderByCreatedAtDesc("PENDING_REVIEW")
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SuperAdminPaymentResponse> findAll() {
        return paymentRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public void approve(Long paymentId, ApprovesPaymentRequest request) {
        SubscriptionPayment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new EntityNotFoundException("Pago no encontrado: " + paymentId));

        if (!"PENDING_REVIEW".equalsIgnoreCase(payment.getStatus())) {
            throw new IllegalStateException("Solo se pueden aprobar pagos en estado PENDING");
        }

        Tenant tenant = tenantRepository.findById(payment.getTenantId())
                .orElseThrow(() -> new EntityNotFoundException("Tenant no encontrado: " + payment.getTenantId()));

        Subscription subscription = subscriptionRepository.findByTenantId(tenant.getId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Suscripción no encontrada para tenant: " + tenant.getId()
                ));

        String requestedPlan = upperOrFallback(payment.getRequestedPlan(), subscription.getPlan());
        String requestedBillingCycle = upperOrFallback(
                payment.getRequestedBillingCycle(),
                subscription.getBillingCycle()
        );

        LocalDateTime now = nowLima();

        payment.setStatus("APPROVED");
        payment.setReviewedAt(now);
        payment.setReviewedByUserId(
                request != null && request.getApprovedBy() != null && !request.getApprovedBy().isBlank()
                        ? 1L
                        : null
        );

        if (request != null && request.getNotes() != null && !request.getNotes().isBlank()) {
            payment.setNotes(request.getNotes());
        }

        paymentRepository.save(payment);

        subscription.setPlan(requestedPlan);
        subscription.setBillingCycle(requestedBillingCycle);
        subscription.setTrial(false);
        subscription.setEstado("ACTIVE");
        subscription.setFechaInicio(now);
        subscription.setFechaRenovacion(calculateEndDate(now, requestedBillingCycle));
        subscription.setFechaFin(calculateEndDate(now, requestedBillingCycle));
        subscription.setObservaciones("Suscripción activada por aprobación de pago");

        if (payment.getAmount() != null) {
            subscription.setPrecioMensual(
                    resolveMonthlyPriceFromPayment(payment.getAmount(), requestedBillingCycle)
            );
        }

        applyPlanLimits(subscription, requestedPlan);
        subscriptionRepository.save(subscription);

        tenant.setActive(true);
        tenant.setFechaActualizacion(now);
        tenantRepository.save(tenant);
    }

    @Override
    public void reject(Long paymentId, RejectPaymentRequest request) {
        SubscriptionPayment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new EntityNotFoundException("Pago no encontrado: " + paymentId));

        if (!"PENDING_REVIEW".equalsIgnoreCase(payment.getStatus())) {
            throw new IllegalStateException("Solo se pueden rechazar pagos en estado PENDING");
        }

        LocalDateTime now = nowLima();

        payment.setStatus("REJECTED");
        payment.setReviewedAt(now);
        payment.setReviewedByUserId(
                request != null && request.getRejectedBy() != null && !request.getRejectedBy().isBlank()
                        ? 1L
                        : null
        );
        payment.setRejectionReason(request != null ? request.getReason() : null);

        paymentRepository.save(payment);
    }

    private SuperAdminPaymentResponse mapToResponse(SubscriptionPayment payment) {
        Tenant tenant = null;
        if (payment.getTenantId() != null) {
            tenant = tenantRepository.findById(payment.getTenantId()).orElse(null);
        }

        return SuperAdminPaymentResponse.builder()
                .paymentId(payment.getId())
                .tenantId(payment.getTenantId())
                .businessName(tenant != null ? tenant.getNombre() : null)
                .plan(payment.getRequestedPlan())
                .billingCycle(payment.getRequestedBillingCycle())
                .amount(payment.getAmount())
                .currency(null)
                .operationNumber(payment.getOperationNumber())
                .payerName(payment.getPayerName())
                .status(payment.getStatus())
                .createdAt(payment.getCreatedAt())
                .build();
    }

    private String upperOrFallback(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim().toUpperCase();
    }

    private LocalDateTime calculateEndDate(LocalDateTime start, String billingCycle) {
        return switch (upperOrFallback(billingCycle, "MONTHLY")) {
            case "SEMIANNUAL" -> start.plusMonths(6);
            case "ANNUAL", "YEARLY" -> start.plusYears(1);
            default -> start.plusMonths(1);
        };
    }

    private Double resolveMonthlyPriceFromPayment(BigDecimal amount, String billingCycle) {
        BigDecimal monthly = switch (upperOrFallback(billingCycle, "MONTHLY")) {
            case "SEMIANNUAL" -> amount.divide(BigDecimal.valueOf(6), 2, RoundingMode.HALF_UP);
            case "ANNUAL", "YEARLY" -> amount.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
            default -> amount;
        };

        return monthly.doubleValue();
    }

    private void applyPlanLimits(Subscription subscription, String plan) {
        switch (upperOrFallback(plan, "STARTER")) {
            case "PRO" -> {
                subscription.setMaxBranches(3);
                subscription.setMaxBarbers(15);
                subscription.setMaxAdmins(3);
                subscription.setAiEnabled(false);
                subscription.setLoyaltyEnabled(true);
                subscription.setPromotionsEnabled(true);
                subscription.setCustomRewardsEnabled(true);
            }
            case "GODS_AI" -> {
                subscription.setMaxBranches(10);
                subscription.setMaxBarbers(50);
                subscription.setMaxAdmins(10);
                subscription.setAiEnabled(true);
                subscription.setLoyaltyEnabled(true);
                subscription.setPromotionsEnabled(true);
                subscription.setCustomRewardsEnabled(true);
            }
            default -> {
                subscription.setMaxBranches(1);
                subscription.setMaxBarbers(5);
                subscription.setMaxAdmins(1);
                subscription.setAiEnabled(false);
                subscription.setLoyaltyEnabled(true);
                subscription.setPromotionsEnabled(false);
                subscription.setCustomRewardsEnabled(false);
            }
        }
    }

    private LocalDateTime nowLima() {
        return LocalDateTime.now(ZoneId.of(DEFAULT_TIMEZONE));
    }
}
package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.ChangePlancRequest;
import com.gods.saas.domain.dto.request.SuperAdminCreateTenantRequest;
import com.gods.saas.domain.dto.response.SuperAdminDashboardResponse;
import com.gods.saas.domain.dto.response.SuperAdminTenantResponse;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import com.gods.saas.service.impl.impl.SuperAdminTenantService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class SuperAdminTenantServiceImpl implements SuperAdminTenantService {
    private final TenantSettingsRepository tenantSettingsRepository;
    private final TenantRepository tenantRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPaymentRepository subscriptionPaymentRepository;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserTenantRoleRepository userTenantRoleRepository;
    private final BranchRepository  branchRepository;

    @Override
    @Transactional(readOnly = true)
    public List<SuperAdminTenantResponse> findAll() {
        return tenantRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SuperAdminTenantResponse findById(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant no encontrado: " + tenantId));

        return mapToResponse(tenant);
    }

    @Override
    public SuperAdminTenantResponse create(SuperAdminCreateTenantRequest request) {
        validateCreateRequest(request);

        if (appUserRepository.existsByEmail(request.getOwnerEmail())) {
            throw new IllegalArgumentException("Ya existe un usuario con ese email: " + request.getOwnerEmail());
        }

        LocalDateTime now = LocalDateTime.now();

        String plan = safeUpper(request.getPlan(), "STARTER");
        String billingCycle = safeUpper(request.getBillingCycle(), "MONTHLY");
        String currency = safeUpper(request.getCurrency(), "USD");
        int trialDays = request.getTrialDays() != null ? request.getTrialDays() : 7;

        String estado = trialDays > 0 ? "TRIAL" : "ACTIVE";

        // 1. TENANT
        Tenant tenant = new Tenant();
        tenant.setNombre(request.getBusinessName());
        tenant.setOwnerName(request.getOwnerName());
        tenant.setPlan(plan);
        tenant.setEstadoSuscripcion(estado);
        tenant.setCodigo(generateTenantCode(request.getBusinessName()));
        tenant.setActive(true);
        tenant.setFechaCreacion(now);
        tenant.setFechaActualizacion(now);
        tenant = tenantRepository.save(tenant);

        // 2. BRANCH PRINCIPAL
        Branch branch = new Branch();
        branch.setTenant(tenant);
        branch.setNombre(
                request.getBranchName() != null && !request.getBranchName().isBlank()
                        ? request.getBranchName().trim()
                        : "Principal"
        );
        branch.setDireccion(request.getBranchAddress());
        branch.setTelefono(
                request.getBranchPhone() != null && !request.getBranchPhone().isBlank()
                        ? request.getBranchPhone().trim()
                        : request.getOwnerPhone()
        );
        branch.setActivo(true);
        branch.setFechaCreacion(now);
        branch = branchRepository.save(branch);

        // 3. OWNER
        AppUser owner = new AppUser();
        owner.setTenant(tenant);
        owner.setBranch(branch); // clave
        owner.setNombre(request.getOwnerName());
        owner.setEmail(request.getOwnerEmail().trim().toLowerCase());
        owner.setPhone(request.getOwnerPhone());
        owner.setPasswordHash(passwordEncoder.encode("123456"));
        owner.setRol("OWNER");
        owner.setActivo(true);
        owner.setFechaCreacion(now);
        owner.setFechaActualizacion(now);
        owner = appUserRepository.save(owner);

        // 4. USER_TENANT_ROLES
        UserTenantRole role = new UserTenantRole();
        role.setUser(owner);
        role.setTenant(tenant);
        role.setRole(RoleType.OWNER);
        role.setBranch(branch); // clave
        userTenantRoleRepository.save(role);

        // 5. SUBSCRIPTION
        Subscription subscription = new Subscription();
        subscription.setTenantId(tenant.getId());
        subscription.setPlan(plan);
        subscription.setBillingCycle(billingCycle);
        subscription.setCurrency(currency);
        subscription.setTrial(trialDays > 0);
        subscription.setEstado(estado);
        subscription.setPrecioMensual(resolveBaseMonthlyPrice(plan));
        subscription.setFechaInicio(now);
        subscription.setFechaRenovacion(calculateEndDate(now, billingCycle, trialDays));
        subscription.setFechaFin(calculateEndDate(now, billingCycle, trialDays));
        subscription.setObservaciones(
                trialDays > 0
                        ? "Trial inicial creado por Super Admin"
                        : "Suscripción creada por Super Admin"
        );

        applyPlanLimits(subscription, plan);

        if (subscription.getMaxBranches() == null) subscription.setMaxBranches(0);
        if (subscription.getMaxBarbers() == null) subscription.setMaxBarbers(0);
        if (subscription.getMaxAdmins() == null) subscription.setMaxAdmins(1);

        subscriptionRepository.save(subscription);

        // 6. TENANT_SETTINGS
        TenantSettings settings = new TenantSettings();
        settings.setTenant(tenant);
        settings.setLanguage("es");
        settings.setTimezone("America/Lima");
        settings.setCurrency(currency);
        settings.setScheduleConfig(new HashMap<>());
        settings.setCreatedAt(now);
        settings.setUpdatedAt(now);
        tenantSettingsRepository.save(settings);

        return mapToResponse(tenant);
    }

    @Override
    public void activate(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant no encontrado: " + tenantId));

        tenant.setActive(true);
        tenant.setFechaActualizacion(LocalDateTime.now());
        tenantRepository.save(tenant);

        Optional<Subscription> subOpt = subscriptionRepository.findById(tenantId);
        if (subOpt.isPresent()) {
            Subscription sub = subOpt.get();

            if ("SUSPENDED".equalsIgnoreCase(sub.getEstado()) ||
                    "EXPIRED".equalsIgnoreCase(sub.getEstado())) {
                sub.setEstado("ACTIVE");
            }

            subscriptionRepository.save(sub);
        }
    }
    private String generateTenantCode(String businessName) {
        String base = businessName
                .toUpperCase()
                .replaceAll("[^A-Z0-9 ]", "")
                .trim()
                .replaceAll("\\s+", "-");

        if (base.length() > 15) {
            base = base.substring(0, 15);
        }

        String code = base;
        int i = 2;

        while (tenantRepository.existsByCodigo(code)) {
            code = base + "-" + i;
            i++;
        }

        return code;
    }
    @Override
    public void suspend(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant no encontrado: " + tenantId));

        tenant.setActive(false);
        tenant.setFechaActualizacion(LocalDateTime.now());
        tenantRepository.save(tenant);

        Optional<Subscription> subOpt = subscriptionRepository.findById(tenantId);
        if (subOpt.isPresent()) {
            Subscription sub = subOpt.get();
            sub.setEstado("SUSPENDED");
            subscriptionRepository.save(sub);
        }
    }

    @Override
    public void changePlan(Long tenantId, ChangePlancRequest request) {
        Subscription subscription = subscriptionRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Suscripción no encontrada para tenant: " + tenantId));

        String newPlan = safeUpper(request.getPlan(), subscription.getPlan());
        String newBillingCycle = safeUpper(request.getBillingCycle(), subscription.getBillingCycle());
        String newCurrency = safeUpper(request.getCurrency(), subscription.getCurrency());

        subscription.setPlan(newPlan);
        subscription.setBillingCycle(newBillingCycle);
        subscription.setCurrency(newCurrency);
        subscription.setEstado("ACTIVE");
        subscription.setTrial(false);

        if (request.getPrice() != null) {
            subscription.setPrecioMensual(request.getPrice().doubleValue());
        } else {
            subscription.setPrecioMensual(resolveBaseMonthlyPrice(newPlan));
        }

        String observations = request.getObservations();
        subscription.setObservaciones(
                observations != null && !observations.isBlank()
                        ? observations
                        : "Cambio manual de plan por Super Admin"
        );

        LocalDateTime now = LocalDateTime.now();
        subscription.setFechaInicio(now);
        subscription.setFechaRenovacion(calculateEndDate(now, newBillingCycle, 0));
        subscription.setFechaFin(calculateEndDate(now, newBillingCycle, 0));

        applyPlanLimits(subscription, newPlan);

        subscriptionRepository.save(subscription);
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Marca no encontrada: " + tenantId));

        if (tenant != null) {
            tenant.setActive(true);
            tenant.setFechaActualizacion(now);
            tenantRepository.save(tenant);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public SuperAdminDashboardResponse dashboard() {
        long totalTenants = tenantRepository.count();
        long activeTenants = subscriptionRepository.countByEstado("ACTIVE");
        long trialTenants = subscriptionRepository.countByEstado("TRIAL");
        long expiredTenants = subscriptionRepository.countByEstado("EXPIRED");
        long suspendedTenants = subscriptionRepository.countByEstado("SUSPENDED");
        long pendingPayments = subscriptionPaymentRepository.countByStatus("PENDING_REVIEW");

        return SuperAdminDashboardResponse.builder()
                .totalTenants(totalTenants)
                .activeTenants(activeTenants)
                .trialTenants(trialTenants)
                .expiredTenants(expiredTenants)
                .suspendedTenants(suspendedTenants)
                .pendingPayments(pendingPayments)
                .build();
    }

    private SuperAdminTenantResponse mapToResponse(Tenant tenant) {
        Subscription subscription = subscriptionRepository.findById(tenant.getId()).orElse(null);

        AppUser owner = appUserRepository
                .findFirstByTenantIdAndRolOrderByIdAsc(tenant.getId(), "OWNER")
                .orElse(null);

        return SuperAdminTenantResponse.builder()
                .tenantId(tenant.getId())
                .businessName(tenant.getNombre())
                .ownerName(owner != null ? owner.getNombre() : null)
                .ownerEmail(owner != null ? owner.getEmail() : null)
                .plan(subscription != null ? subscription.getPlan() : null)
                .status(subscription != null ? subscription.getEstado() : null)
                .billingCycle(subscription != null ? subscription.getBillingCycle() : null)
                .fechaInicio(subscription != null ? subscription.getFechaInicio() : null)
                .fechaFin(subscription != null ? subscription.getFechaFin() : null)
                .build();
    }

    private void validateCreateRequest(SuperAdminCreateTenantRequest request) {
        if (request.getBusinessName() == null || request.getBusinessName().isBlank()) {
            throw new IllegalArgumentException("El nombre del negocio es obligatorio");
        }
        if (request.getOwnerName() == null || request.getOwnerName().isBlank()) {
            throw new IllegalArgumentException("El nombre del dueño es obligatorio");
        }
        if (request.getOwnerEmail() == null || request.getOwnerEmail().isBlank()) {
            throw new IllegalArgumentException("El email del dueño es obligatorio");
        }
    }

    private String safeUpper(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim().toUpperCase();
    }

    private LocalDateTime calculateEndDate(LocalDateTime start, String billingCycle, int trialDays) {
        if (trialDays > 0) {
            return start.plusDays(trialDays);
        }

        return switch (safeUpper(billingCycle, "MONTHLY")) {
            case "SEMIANNUAL" -> start.plusMonths(6);
            case "ANNUAL", "YEARLY" -> start.plusYears(1);
            default -> start.plusMonths(1);
        };
    }

    private Double resolveBaseMonthlyPrice(String plan) {
        return switch (safeUpper(plan, "STARTER")) {
            case "PRO" -> BigDecimal.valueOf(19).doubleValue();
            case "GODS_AI" -> BigDecimal.valueOf(39).doubleValue();
            default -> BigDecimal.valueOf(9).doubleValue();
        };
    }

    private void applyPlanLimits(Subscription subscription, String plan) {
        switch (safeUpper(plan, "STARTER")) {
            case "PRO" -> {
                subscription.setMaxBranches(3);
                subscription.setMaxBarbers(15);
                subscription.setMaxAdmins(3);
                subscription.setAiEnabled(false);
                subscription.setLoyaltyEnabled(true);
                subscription.setPromotionsEnabled(true);
                subscription.setCustomRewardsEnabled(true); //
            }
            case "GODS_AI" -> {
                subscription.setMaxBranches(10);
                subscription.setMaxBarbers(50);
                subscription.setMaxAdmins(10);
                subscription.setAiEnabled(true);
                subscription.setLoyaltyEnabled(true);
                subscription.setPromotionsEnabled(true);
                subscription.setCustomRewardsEnabled(true); //
            }
            default -> {
                subscription.setMaxBranches(1);
                subscription.setMaxBarbers(5);
                subscription.setMaxAdmins(1);
                subscription.setAiEnabled(false);
                subscription.setLoyaltyEnabled(true);
                subscription.setPromotionsEnabled(false);
                subscription.setCustomRewardsEnabled(false); // 🔥
            }
        }
    }
}
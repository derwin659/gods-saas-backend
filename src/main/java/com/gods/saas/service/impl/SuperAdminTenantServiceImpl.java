package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.ChangePlancRequest;
import com.gods.saas.domain.dto.request.SuperAdminCreateTenantRequest;
import com.gods.saas.domain.dto.request.SuperAdminUpdateTenantRequest;
import com.gods.saas.domain.dto.response.SuperAdminDashboardResponse;
import com.gods.saas.domain.dto.response.SuperAdminTenantResponse;
import com.gods.saas.domain.enums.BusinessType;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import com.gods.saas.service.impl.impl.SuperAdminTenantService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class SuperAdminTenantServiceImpl implements SuperAdminTenantService {

    private static final String DEFAULT_TIMEZONE = "America/Lima";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_TRIAL = "TRIAL";
    private static final String STATUS_EXPIRED = "EXPIRED";
    private static final String STATUS_SUSPENDED = "SUSPENDED";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String STATUS_NO_SUBSCRIPTION = "NO_SUBSCRIPTION";

    private final TenantSettingsRepository tenantSettingsRepository;
    private final TenantRepository tenantRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPaymentRepository subscriptionPaymentRepository;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserTenantRoleRepository userTenantRoleRepository;
    private final BranchRepository branchRepository;
    private final SubscriptionPlanPricingService pricingService;

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

        LocalDateTime now = nowLima();

        String plan = safeUpper(request.getPlan(), "STARTER");
        String billingCycle = safeUpper(request.getBillingCycle(), "MONTHLY");
        String country = safeTrim(request.getCountry(), "Peru");
        String currency = resolveCurrencyForCountry(country, request.getCurrency());
        int trialDays = request.getTrialDays() != null ? request.getTrialDays() : 7;

        String estado = trialDays > 0 ? "TRIAL" : "ACTIVE";

        Tenant tenant = new Tenant();
        tenant.setNombre(request.getBusinessName());
        tenant.setOwnerName(request.getOwnerName());
        tenant.setPlan(plan);
        tenant.setEstadoSuscripcion(estado);
        tenant.setCodigo(generateTenantCode(request.getBusinessName()));
        tenant.setBusinessType(resolveBusinessType(request.getBusinessType()));
        tenant.setPais(country);
        tenant.setActive(true);
        tenant.setFechaCreacion(now);
        tenant.setFechaActualizacion(now);
        tenant = tenantRepository.save(tenant);

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

        AppUser owner = new AppUser();
        owner.setTenant(tenant);
        owner.setBranch(branch);
        owner.setNombre(request.getOwnerName());
        owner.setEmail(request.getOwnerEmail().trim().toLowerCase());
        owner.setPhone(request.getOwnerPhone());
        owner.setPasswordHash(passwordEncoder.encode("123456"));
        owner.setRol("OWNER");
        owner.setActivo(true);
        owner.setFechaCreacion(now);
        owner.setFechaActualizacion(now);
        owner = appUserRepository.save(owner);

        UserTenantRole role = new UserTenantRole();
        role.setUser(owner);
        role.setTenant(tenant);
        role.setRole(RoleType.OWNER);
        role.setBranch(branch);
        userTenantRoleRepository.save(role);

        Subscription subscription = new Subscription();
        subscription.setTenantId(tenant.getId());
        subscription.setPlan(plan);
        subscription.setBillingCycle(billingCycle);
        subscription.setCurrency(currency);
        subscription.setTrial(trialDays > 0);
        subscription.setEstado(estado);
        subscription.setPrecioMensual(pricingService.resolveMonthlyPrice(plan, country, currency).doubleValue());
        subscription.setFechaInicio(now);
        subscription.setFechaRenovacion(calculateEndDate(now, billingCycle, trialDays));
        subscription.setFechaFin(calculateEndDate(now, billingCycle, trialDays));
        subscription.setObservaciones(
                trialDays > 0
                        ? "Trial inicial creado por Super Admin"
                        : "SuscripciÃ³n creada por Super Admin"
        );

        applyPlanLimits(subscription, plan);

        if (subscription.getMaxBranches() == null) subscription.setMaxBranches(0);
        if (subscription.getMaxBarbers() == null) subscription.setMaxBarbers(0);
        if (subscription.getMaxAdmins() == null) subscription.setMaxAdmins(1);

        subscriptionRepository.save(subscription);

        TenantSettings settings = new TenantSettings();
        settings.setTenant(tenant);
        settings.setLanguage("es");
        settings.setTimezone(DEFAULT_TIMEZONE);
        settings.setCurrency(currency);
        settings.setScheduleConfig(new HashMap<>());
        settings.setCreatedAt(now);
        settings.setUpdatedAt(now);
        tenantSettingsRepository.save(settings);

        return mapToResponse(tenant);
    }

    @Override
    public SuperAdminTenantResponse update(Long tenantId, SuperAdminUpdateTenantRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant no encontrado: " + tenantId));

        LocalDateTime now = nowLima();

        if (hasText(request.getBusinessName())) {
            tenant.setNombre(request.getBusinessName().trim());
        }
        if (hasText(request.getOwnerName())) {
            tenant.setOwnerName(request.getOwnerName().trim());
        }
        if (hasText(request.getCountry())) {
            tenant.setPais(request.getCountry().trim());
        }

        AppUser owner = appUserRepository
                .findFirstByTenantIdAndRolOrderByIdAsc(tenantId, "OWNER")
                .orElse(null);

        if (owner != null) {
            if (hasText(request.getOwnerEmail())) {
                String email = request.getOwnerEmail().trim().toLowerCase();
                if (appUserRepository.existsByEmailAndTenant_IdAndIdNot(email, tenantId, owner.getId())) {
                    throw new IllegalArgumentException("Ya existe otro usuario con ese email en este negocio: " + email);
                }
                owner.setEmail(email);
            }
            if (hasText(request.getOwnerName())) {
                owner.setNombre(request.getOwnerName().trim());
            }
            if (request.getOwnerPhone() != null) {
                owner.setPhone(request.getOwnerPhone().trim());
            }
            owner.setFechaActualizacion(now);
            appUserRepository.save(owner);
        }

        Optional<Subscription> subscriptionOpt = findSubscription(tenantId);
        subscriptionOpt.ifPresent(subscription -> {
            if (hasText(request.getPlan())) {
                String plan = safeUpper(request.getPlan(), subscription.getPlan());
                subscription.setPlan(plan);
                tenant.setPlan(plan);
                applyPlanLimits(subscription, plan);
            }
            if (hasText(request.getBillingCycle())) {
                subscription.setBillingCycle(safeUpper(request.getBillingCycle(), subscription.getBillingCycle()));
            }
            if (hasText(request.getCurrency())) {
                subscription.setCurrency(safeUpper(request.getCurrency(), subscription.getCurrency()));
            }
            if (request.getPrice() != null) {
                subscription.setPrecioMensual(request.getPrice().doubleValue());
            }
            if (hasText(request.getStatus())) {
                String status = safeUpper(request.getStatus(), subscription.getEstado());
                subscription.setEstado(status);
                tenant.setEstadoSuscripcion(status);
                tenant.setActive(!STATUS_SUSPENDED.equals(status) && !STATUS_CANCELLED.equals(status));
                subscription.setTrial(STATUS_TRIAL.equals(status));
            }
            if (request.getFechaInicio() != null) {
                subscription.setFechaInicio(request.getFechaInicio());
            }
            if (request.getFechaFin() != null) {
                subscription.setFechaFin(request.getFechaFin());
                subscription.setFechaRenovacion(request.getFechaFin());
            }
            if (hasText(request.getObservations())) {
                subscription.setObservaciones(appendObservation(
                        subscription.getObservaciones(),
                        request.getObservations().trim()
                ));
            }
            subscriptionRepository.save(subscription);
        });

        tenant.setFechaActualizacion(now);
        tenantRepository.save(tenant);

        return mapToResponse(tenant);
    }

    private String resolveBusinessType(BusinessType businessType) {
        if (businessType == null) {
            return BusinessType.BARBERSHOP.name();
        }

        return businessType.name();
    }

    @Override
    public void activate(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant no encontrado: " + tenantId));

        LocalDateTime now = nowLima();

        tenant.setActive(true);
        tenant.setEstadoSuscripcion(STATUS_ACTIVE);
        tenant.setFechaActualizacion(now);
        tenantRepository.save(tenant);

        Optional<Subscription> subOpt = findSubscription(tenantId);
        if (subOpt.isPresent()) {
            Subscription sub = subOpt.get();

            if (STATUS_SUSPENDED.equalsIgnoreCase(sub.getEstado()) ||
                    STATUS_EXPIRED.equalsIgnoreCase(sub.getEstado()) ||
                    STATUS_CANCELLED.equalsIgnoreCase(sub.getEstado())) {
                sub.setEstado(STATUS_ACTIVE);
                sub.setTrial(false);
            }

            if (sub.getFechaFin() == null || sub.getFechaFin().isBefore(now)) {
                sub.setFechaInicio(now);
                sub.setFechaRenovacion(calculateEndDate(now, sub.getBillingCycle(), 0));
                sub.setFechaFin(calculateEndDate(now, sub.getBillingCycle(), 0));
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

        LocalDateTime now = nowLima();

        tenant.setActive(false);
        tenant.setEstadoSuscripcion(STATUS_SUSPENDED);
        tenant.setFechaActualizacion(now);
        tenantRepository.save(tenant);

        Optional<Subscription> subOpt = findSubscription(tenantId);
        if (subOpt.isPresent()) {
            Subscription sub = subOpt.get();
            sub.setEstado(STATUS_SUSPENDED);
            subscriptionRepository.save(sub);
        }
    }

    @Override
    public void deleteTenant(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant no encontrado: " + tenantId));

        LocalDateTime now = nowLima();

        tenant.setActive(false);
        tenant.setEstadoSuscripcion(STATUS_CANCELLED);
        tenant.setFechaActualizacion(now);
        tenantRepository.save(tenant);

        findSubscription(tenantId).ifPresent(subscription -> {
            subscription.setEstado(STATUS_CANCELLED);
            subscription.setTrial(false);
            subscription.setFechaFin(now);
            subscription.setObservaciones(appendObservation(
                    subscription.getObservaciones(),
                    "Cuenta cancelada por Super Admin"
            ));
            subscriptionRepository.save(subscription);
        });
    }

    @Override
    public void changePlan(Long tenantId, ChangePlancRequest request) {
        Subscription subscription = findSubscription(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("SuscripciÃ³n no encontrada para tenant: " + tenantId));

        String newPlan = safeUpper(request.getPlan(), subscription.getPlan());
        String newBillingCycle = safeUpper(request.getBillingCycle(), subscription.getBillingCycle());
        String newCurrency = safeUpper(request.getCurrency(), subscription.getCurrency());

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Marca no encontrada: " + tenantId));

        subscription.setPlan(newPlan);
        subscription.setBillingCycle(newBillingCycle);
        subscription.setCurrency(newCurrency);
        subscription.setEstado(STATUS_ACTIVE);
        subscription.setTrial(false);

        if (request.getPrice() != null) {
            subscription.setPrecioMensual(request.getPrice().doubleValue());
        } else {
            subscription.setPrecioMensual(pricingService.resolveMonthlyPrice(newPlan, tenant.getPais(), newCurrency).doubleValue());
        }

        String observations = request.getObservations();
        subscription.setObservaciones(
                observations != null && !observations.isBlank()
                        ? observations
                        : "Cambio manual de plan por Super Admin"
        );

        LocalDateTime now = nowLima();
        subscription.setFechaInicio(now);
        subscription.setFechaRenovacion(calculateEndDate(now, newBillingCycle, 0));
        subscription.setFechaFin(calculateEndDate(now, newBillingCycle, 0));

        applyPlanLimits(subscription, newPlan);

        subscriptionRepository.save(subscription);

        tenant.setActive(true);
        tenant.setEstadoSuscripcion(STATUS_ACTIVE);
        tenant.setFechaActualizacion(now);
        tenantRepository.save(tenant);
    }

    @Override
    @Transactional(readOnly = true)
    public SuperAdminDashboardResponse dashboard() {
        long totalTenants = tenantRepository.count();
        LocalDateTime now = nowLima();
        List<SuperAdminTenantResponse> tenants = tenantRepository.findAll()
                .stream()
                .map(tenant -> mapToResponse(tenant, now))
                .toList();
        long activeTenants = countByStatus(tenants, STATUS_ACTIVE);
        long trialTenants = countByStatus(tenants, STATUS_TRIAL);
        long expiredTenants = countByStatus(tenants, STATUS_EXPIRED);
        long suspendedTenants = countByStatus(tenants, STATUS_SUSPENDED);
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
        return mapToResponse(tenant, nowLima());
    }

    private SuperAdminTenantResponse mapToResponse(Tenant tenant, LocalDateTime now) {
        Subscription subscription = findSubscription(tenant.getId()).orElse(null);

        AppUser owner = appUserRepository
                .findFirstByTenantIdAndRolOrderByIdAsc(tenant.getId(), "OWNER")
                .orElse(null);

        String effectiveStatus = resolveEffectiveStatus(tenant, subscription, now);

        return SuperAdminTenantResponse.builder()
                .tenantId(tenant.getId())
                .businessName(tenant.getNombre())
                .ownerName(owner != null ? owner.getNombre() : null)
                .ownerEmail(owner != null ? owner.getEmail() : null)
                .ownerPhone(owner != null ? owner.getPhone() : null)
                .plan(subscription != null ? subscription.getPlan() : null)
                .status(effectiveStatus)
                .rawStatus(subscription != null ? subscription.getEstado() : null)
                .tenantActive(Boolean.TRUE.equals(tenant.getActive()))
                .trial(subscription != null ? subscription.isTrial() : null)
                .daysRemaining(calculateDaysRemaining(subscription, now))
                .billingCycle(subscription != null ? subscription.getBillingCycle() : null)
                .fechaInicio(subscription != null ? subscription.getFechaInicio() : null)
                .fechaFin(subscription != null ? subscription.getFechaFin() : null)
                .build();
    }

    private Optional<Subscription> findSubscription(Long tenantId) {
        return subscriptionRepository.findTopByTenantIdOrderBySubIdDesc(tenantId)
                .or(() -> subscriptionRepository.findByTenantId(tenantId));
    }

    private long countByStatus(List<SuperAdminTenantResponse> tenants, String status) {
        return tenants.stream()
                .filter(item -> status.equalsIgnoreCase(item.getStatus()))
                .count();
    }

    private String resolveEffectiveStatus(Tenant tenant, Subscription subscription, LocalDateTime now) {
        if (subscription == null) {
            return Boolean.TRUE.equals(tenant.getActive()) ? STATUS_NO_SUBSCRIPTION : STATUS_CANCELLED;
        }

        String rawStatus = safeUpper(subscription.getEstado(), STATUS_NO_SUBSCRIPTION);

        if (STATUS_CANCELLED.equals(rawStatus)) {
            return STATUS_CANCELLED;
        }

        if (STATUS_SUSPENDED.equals(rawStatus) || Boolean.FALSE.equals(tenant.getActive())) {
            return STATUS_SUSPENDED;
        }

        if (subscription.getFechaFin() != null && subscription.getFechaFin().isBefore(now)) {
            return STATUS_EXPIRED;
        }

        if (subscription.isTrial() || STATUS_TRIAL.equals(rawStatus)) {
            return STATUS_TRIAL;
        }

        if (STATUS_ACTIVE.equals(rawStatus)) {
            return STATUS_ACTIVE;
        }

        return rawStatus;
    }

    private Long calculateDaysRemaining(Subscription subscription, LocalDateTime now) {
        if (subscription == null || subscription.getFechaFin() == null) {
            return null;
        }

        return ChronoUnit.DAYS.between(now.toLocalDate(), subscription.getFechaFin().toLocalDate());
    }

    private String appendObservation(String current, String next) {
        if (current == null || current.isBlank()) {
            return next;
        }

        return current + " | " + next;
    }

    private void validateCreateRequest(SuperAdminCreateTenantRequest request) {
        if (request.getBusinessName() == null || request.getBusinessName().isBlank()) {
            throw new IllegalArgumentException("El nombre del negocio es obligatorio");
        }
        if (request.getOwnerName() == null || request.getOwnerName().isBlank()) {
            throw new IllegalArgumentException("El nombre del dueÃ±o es obligatorio");
        }
        if (request.getOwnerEmail() == null || request.getOwnerEmail().isBlank()) {
            throw new IllegalArgumentException("El email del dueÃ±o es obligatorio");
        }
    }

    private String safeUpper(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim().toUpperCase();
    }

    private String safeTrim(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String resolveCurrencyForCountry(String country, String requestedCurrency) {
        if (requestedCurrency != null && !requestedCurrency.isBlank()) {
            return safeUpper(requestedCurrency, "PEN");
        }

        String normalizedCountry = normalizeCountry(country);
        return switch (normalizedCountry) {
            case "PERU", "PE" -> "PEN";
            case "ESTADOSUNIDOS", "UNITEDSTATES", "USA", "US" -> "USD";
            case "COLOMBIA", "CO" -> "COP";
            case "MEXICO", "MX" -> "MXN";
            case "CHILE", "CL" -> "CLP";
            case "ARGENTINA", "AR" -> "ARS";
            case "BOLIVIA", "BO" -> "BOB";
            case "BRASIL", "BRAZIL", "BR" -> "BRL";
            case "VENEZUELA", "VE" -> "VES";
            case "URUGUAY", "UY" -> "UYU";
            case "PARAGUAY", "PY" -> "PYG";
            case "COSTARICA", "CR" -> "CRC";
            case "REPUBLICADOMINICANA", "DOMINICANREPUBLIC", "DO" -> "DOP";
            case "GUATEMALA", "GT" -> "GTQ";
            case "ESPANA", "SPAIN", "EUROPA", "EUROPE", "EU" -> "EUR";
            default -> "PEN";
        };
    }

    private String normalizeCountry(String value) {
        if (value == null) {
            return "";
        }

        return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z]", "")
                .toUpperCase();
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

    private void applyPlanLimits(Subscription subscription, String plan) {
        switch (safeUpper(plan, "STARTER")) {
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



package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.AdminDashboardDTO;
import com.gods.saas.domain.dto.TenantDto;
import com.gods.saas.domain.dto.TenantPayloadDto;
import com.gods.saas.domain.mapper.TenantMapper;
import com.gods.saas.domain.model.Subscription;
import com.gods.saas.domain.model.Tenant;
import com.gods.saas.domain.model.TenantSettings;
import com.gods.saas.domain.repository.SuscriptionRepository;
import com.gods.saas.domain.repository.TenantRepository;
import com.gods.saas.domain.repository.TenantSettingsRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantService {

    private final TenantRepository tenantRepo;
    private final TenantSettingsRepository settingsRepo;
    private final SuscriptionRepository subscriptionRepository;
    private final SubscriptionPlanPricingService pricingService;


    // ------------------------------------------------------
    // 1ï¸âƒ£ Crear Tenant
    // ------------------------------------------------------
    public Tenant createTenant(Tenant tenant) {

        log.info("Entrada a crear {}", tenant.toString());

        tenant.setActive(true);

        if (tenant.getBusinessType() == null || tenant.getBusinessType().isBlank()) {
            tenant.setBusinessType("BARBERSHOP");
        }

        tenant.setFechaCreacion(LocalDateTime.now(ZoneOffset.UTC));
        tenant.setFechaActualizacion(LocalDateTime.now());

        Tenant savedTenant = tenantRepo.save(tenant);

        // Crear settings por defecto
        TenantSettings settings = new TenantSettings();
        settings.setTenant(savedTenant);
        settings.setLanguage("es");
        String currency = pricingService.currencyForCountry(pricingService.countryCodeFor(savedTenant.getPais()), null);
        settings.setCurrency(currency);
        settings.setTimezone("America/Lima");
        settings.setCreatedAt(LocalDateTime.now());

        Subscription subscription = Subscription.builder()
                .tenantId(savedTenant.getId())
                .plan("STARTER")
                .precioMensual(pricingService.resolveMonthlyPrice("STARTER", savedTenant.getPais(), currency).doubleValue())
                .estado("TRIAL")
                .fechaInicio(LocalDateTime.now())
                .fechaRenovacion(LocalDateTime.now().plusDays(7))
                .fechaFin(LocalDateTime.now().plusDays(7))
                .trial(true)
                .diasGracia(0)
                .maxBranches(1)
                .maxBarbers(5)
                .maxAdmins(1)
                .aiEnabled(false)
                .loyaltyEnabled(true)
                .promotionsEnabled(true)
                .billingCycle("MONTHLY")
                .currency(currency)
                .observaciones("Trial inicial al crear tenant")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        subscriptionRepository.save(subscription);

        settingsRepo.save(settings);

        return savedTenant;
    }

    // ------------------------------------------------------
    // 2ï¸âƒ£ Obtener TODOS los tenants
    // ------------------------------------------------------
    public List<TenantDto> getAllTenants() {
        return tenantRepo.findAll()
                .stream()
                .map(TenantMapper::toDto)
                .collect(Collectors.toList());
    }

    // ------------------------------------------------------
    // 3ï¸âƒ£ Obtener SOLO activos
    // ------------------------------------------------------
    public List<Tenant> getActiveTenants() {
        return tenantRepo.findByActiveTrue();
    }

    // ------------------------------------------------------
    // 4ï¸âƒ£ Obtener por ID
    // ------------------------------------------------------
    public Tenant getById(Long id) {
        return tenantRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Tenant no encontrado"));
    }

    // ------------------------------------------------------
    // 5ï¸âƒ£ Actualizar datos del tenant
    // ------------------------------------------------------
    public Tenant update(Long id, Tenant data) {
        Tenant tenant = getById(id);

        tenant.setNombre(data.getNombre());
        tenant.setCiudad(data.getCiudad());
        tenant.setPais(data.getPais());
        tenant.setEstadoSuscripcion(data.getPlan());
        tenant.setPlan(data.getPlan());

        if (data.getBusinessType() != null && !data.getBusinessType().isBlank()) {
            tenant.setBusinessType(data.getBusinessType());
        }

        tenant.setFechaActualizacion(LocalDateTime.now());

        return tenantRepo.save(tenant);
    }

    // ------------------------------------------------------
    // 6ï¸âƒ£ Actualizar Settings
    // ------------------------------------------------------
    public TenantSettings updateSettings(Long tenantId, TenantSettings newSettings) {
        TenantSettings settings = settingsRepo.findByTenantId(tenantId)
                .orElseThrow(() -> new RuntimeException("No settings found para este tenant"));

        settings.setLanguage(newSettings.getLanguage());
        settings.setCurrency(newSettings.getCurrency());
        settings.setTimezone(newSettings.getTimezone());
        settings.setScheduleConfig(newSettings.getScheduleConfig());
        settings.setUpdatedAt(LocalDateTime.now());

        return settingsRepo.save(settings);
    }

    // ------------------------------------------------------
    // 7ï¸âƒ£ Cambiar estado (Activar / Suspender)
    // ------------------------------------------------------

    public void toggleStatus(Long id) {
        Tenant tenant = tenantRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Tenant no encontrado"));

        tenant.setActive(!tenant.getActive());
        tenantRepo.save(tenant);
    }

    @Transactional
    public void delete(Long id) {
        Tenant tenant = tenantRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Tenant no encontrado"));

        tenantRepo.delete(tenant);
    }

    public TenantDto getTenantByCode(String code) {
        Tenant tenant = tenantRepo.findByCodigoIgnoreCaseAndActiveTrue(code.trim().toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontrÃ³ un local con ese cÃ³digo"
                ));
        return TenantMapper.toDto(tenant);
    }




    // ------------------------------------------------------
    // 8ï¸âƒ£ MÃ©tricas del Dashboard del Super Admin
    // ------------------------------------------------------
    public AdminDashboardDTO getDashboardMetrics() {

        long total = tenantRepo.count();
        long active = tenantRepo.countByActiveTrue();
        long inactive = total - active;

        // âš¡ En futuro agregamos ingresos reales â†’ Stripe o tu mÃ³dulo de pagos
        double income = active * 49.90; // SUPUESTO PLAN PREMIUM

        AdminDashboardDTO dto = new AdminDashboardDTO();
        dto.setTotalTenants(total);
        dto.setActiveTenants(active);
        dto.setInactiveTenants(inactive);
        dto.setIncomeThisMonth(income);

        dto.setRecentTenants(
                tenantRepo.findTop5ByOrderByFechaCreacionDesc()
        );

        return dto;
    }
}


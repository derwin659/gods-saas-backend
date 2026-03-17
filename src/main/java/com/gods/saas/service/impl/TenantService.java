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


    // ------------------------------------------------------
    // 1️⃣ Crear Tenant
    // ------------------------------------------------------
    public Tenant createTenant(Tenant tenant) {

        log.info("Entrada a crear {}", tenant.toString());

        tenant.setActive(true); // activar por defecto
        tenant.setFechaCreacion(LocalDateTime.now(ZoneOffset.UTC));
        tenant.setFechaActualizacion(LocalDateTime.now());

        Tenant savedTenant = tenantRepo.save(tenant);

        // Crear settings por defecto
        TenantSettings settings = new TenantSettings();
        settings.setTenant(savedTenant);
        settings.setLanguage("es");
        settings.setCurrency("PEN");
        settings.setTimezone("America/Lima");
        settings.setCreatedAt(LocalDateTime.now());

        Subscription subscription = Subscription.builder()
                .tenant(tenant)
                .plan("FREE")
                .precioMensual(0.0)
                .estado("ACTIVE")
                .fechaInicio(LocalDateTime.now())
                .fechaRenovacion(LocalDateTime.now().plusDays(14))
                .build();

        subscriptionRepository.save(subscription);

        settingsRepo.save(settings);

        return savedTenant;
    }

    // ------------------------------------------------------
    // 2️⃣ Obtener TODOS los tenants
    // ------------------------------------------------------
    public List<TenantDto> getAllTenants() {
        return tenantRepo.findAll()
                .stream()
                .map(TenantMapper::toDto)
                .collect(Collectors.toList());
    }

    // ------------------------------------------------------
    // 3️⃣ Obtener SOLO activos
    // ------------------------------------------------------
    public List<Tenant> getActiveTenants() {
        return tenantRepo.findByActiveTrue();
    }

    // ------------------------------------------------------
    // 4️⃣ Obtener por ID
    // ------------------------------------------------------
    public Tenant getById(Long id) {
        return tenantRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Tenant no encontrado"));
    }

    // ------------------------------------------------------
    // 5️⃣ Actualizar datos del tenant
    // ------------------------------------------------------
    public Tenant update(Long id, Tenant data) {
        Tenant tenant = getById(id);

        tenant.setNombre(data.getNombre());
        tenant.setCiudad(data.getCiudad());
        tenant.setPais(data.getPais());
        tenant.setEstadoSuscripcion(data.getPlan());
        tenant.setPlan(data.getPlan());
        tenant.setFechaActualizacion(LocalDateTime.now());

        return tenantRepo.save(tenant);
    }

    // ------------------------------------------------------
    // 6️⃣ Actualizar Settings
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
    // 7️⃣ Cambiar estado (Activar / Suspender)
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
                        "No se encontró un local con ese código"
                ));
        return TenantMapper.toDto(tenant);
    }




    // ------------------------------------------------------
    // 8️⃣ Métricas del Dashboard del Super Admin
    // ------------------------------------------------------
    public AdminDashboardDTO getDashboardMetrics() {

        long total = tenantRepo.count();
        long active = tenantRepo.countByActiveTrue();
        long inactive = total - active;

        // ⚡ En futuro agregamos ingresos reales → Stripe o tu módulo de pagos
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

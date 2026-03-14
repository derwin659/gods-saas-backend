package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.AdminDashboardDTO;
import com.gods.saas.domain.dto.TenantDto;
import com.gods.saas.domain.dto.TenantPayloadDto;
import com.gods.saas.domain.mapper.TenantMapper;
import com.gods.saas.domain.model.Tenant;
import com.gods.saas.domain.model.TenantSettings;
import com.gods.saas.service.impl.TenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/api/tenants")
@RequiredArgsConstructor
@Slf4j
public class TenantController {

    private final TenantService tenantService;

    // -----------------------------------------
    // 1. CREAR TENANT
    // -----------------------------------------
    @PostMapping
    public ResponseEntity<Tenant> create(@RequestBody Tenant tenant) {
        System.out.println(tenant.toString());
        return ResponseEntity.ok(tenantService.createTenant(tenant));
    }

    // -----------------------------------------
    // 2. LISTAR TODOS
    // -----------------------------------------
    @GetMapping
    public ResponseEntity<List<TenantDto>> getAll() {
        return ResponseEntity.ok(tenantService.getAllTenants());
    }

    // -----------------------------------------
    // 3. LISTAR SOLO ACTIVOS
    // -----------------------------------------
    @GetMapping("/active")
    public ResponseEntity<List<Tenant>> getActive() {
        return ResponseEntity.ok(tenantService.getActiveTenants());
    }

    // -----------------------------------------
    // 4. OBTENER POR ID
    // -----------------------------------------
    @GetMapping("/{id}")
    public Tenant getOne(@PathVariable Long id) {
        return tenantService.getById(id);
    }

    // -----------------------------------------
    // 5. EDITAR TENANT
    // -----------------------------------------
    @PutMapping("/{id}")
    public ResponseEntity<Tenant> update(@PathVariable Long id, @RequestBody Tenant tenant) {
       log.info("entro {}", tenant);
        Tenant updated = tenantService.update(id, tenant);

        log.info("salio {}", updated);

        return ResponseEntity.ok(updated);

    }

    // -----------------------------------------
    // 6. EDITAR SETTINGS DEL TENANT
    // -----------------------------------------
    @PutMapping("/{id}/settings")
    public ResponseEntity<TenantSettings> updateSettings(
            @PathVariable Long id,
            @RequestBody TenantSettings settings
    ) {
        return ResponseEntity.ok(tenantService.updateSettings(id, settings));
    }

    // -----------------------------------------
    // 7. CAMBIAR ESTADO ACTIVO / SUSPENDIDO
    // -----------------------------------------
    @PostMapping("/{id}/status")
    public ResponseEntity<Void> toggleStatus(@PathVariable Long id) {
        tenantService.toggleStatus(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTenant(@PathVariable Long id) {
        tenantService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // -----------------------------------------
    // 8. MÉTRICAS DEL SUPER ADMIN
    // -----------------------------------------
    @GetMapping("/dashboard")
    public ResponseEntity<AdminDashboardDTO> getDashboardMetrics() {
        return ResponseEntity.ok(tenantService.getDashboardMetrics());
    }

    @GetMapping("/by-code/{code}")
    public ResponseEntity<TenantDto> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(tenantService.getTenantByCode(code));
    }


}

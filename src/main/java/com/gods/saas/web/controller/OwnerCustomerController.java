package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.ActualizarClienteRequest;
import com.gods.saas.domain.dto.ClienteResponse;
import com.gods.saas.domain.dto.VentaRapidaRequest;
import com.gods.saas.domain.dto.response.InactiveCustomerResponse;
import com.gods.saas.domain.dto.request.WhatsappConsentRequest;
import com.gods.saas.domain.dto.response.OwnerCustomerHistoryResponse;
import com.gods.saas.domain.dto.response.OwnerCustomerLoyaltyResponse;
import com.gods.saas.domain.model.Customer;
import com.gods.saas.service.impl.AdminPermissionService;
import com.gods.saas.service.impl.CustomerService;
import com.gods.saas.service.impl.CustomerExportService;
import com.gods.saas.service.impl.GeneralAuditService;
import com.gods.saas.utils.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/owner/customers")
@RequiredArgsConstructor
public class OwnerCustomerController {

    private final CustomerService customerService;
    private final JwtUtil jwtUtil;
    private final AdminPermissionService adminPermissionService;
    private final CustomerExportService customerExportService;
    private final GeneralAuditService generalAuditService;

    @Operation(summary = "Listar clientes para owner/admin")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    public ResponseEntity<List<ClienteResponse>> list(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "50") int limit
    ) {
        adminPermissionService.checkPermission("CUSTOMERS_ACCESS");

        Long tenantId = extractTenantId(authHeader);
        boolean canViewPhone = adminPermissionService.hasCurrentUserPermission("CUSTOMERS_VIEW_PHONE");

        List<ClienteResponse> response = customerService
                .listarClientesOwner(tenantId, q, limit)
                .stream()
                .map(customer -> ClienteResponse.fromEntity(
                        customer,
                        customerService.obtenerPuntosDisponiblesReales(tenantId, customer.getId())
                ))
                .map(item -> protectPhone(item, canViewPhone))
                .toList();

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Listar clientes inactivos para campañas")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/inactive")
    public ResponseEntity<List<InactiveCustomerResponse>> inactiveCustomers(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(defaultValue = "30") Integer days
    ) {
        adminPermissionService.checkPermission("CUSTOMERS_ACCESS");

        Long tenantId = extractTenantId(authHeader);
        boolean canViewPhone = adminPermissionService.hasCurrentUserPermission("CUSTOMERS_VIEW_PHONE");

        return ResponseEntity.ok(
                customerService.listarClientesInactivosOwner(tenantId, days)
                        .stream()
                        .map(item -> protectPhone(item, canViewPhone))
                        .toList()
        );
    }

    @Operation(summary = "Obtener detalle de cliente para owner/admin")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{customerId}")
    public ResponseEntity<ClienteResponse> detail(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long customerId
    ) {
        adminPermissionService.checkPermission("CUSTOMERS_ACCESS");

        Long tenantId = extractTenantId(authHeader);
        Customer customer = customerService.obtenerClienteOwner(tenantId, customerId);

        Integer puntosReales = customerService.obtenerPuntosDisponiblesReales(
                tenantId,
                customer.getId()
        );

        boolean canViewPhone = adminPermissionService.hasCurrentUserPermission("CUSTOMERS_VIEW_PHONE");
        return ResponseEntity.ok(protectPhone(ClienteResponse.fromEntity(customer, puntosReales), canViewPhone));
    }

    @Operation(summary = "Obtener puntos reales del cliente para owner/admin")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{customerId}/loyalty")
    public ResponseEntity<OwnerCustomerLoyaltyResponse> loyalty(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long customerId
    ) {
        adminPermissionService.checkPermission("CUSTOMERS_ACCESS");

        Long tenantId = extractTenantId(authHeader);
        return ResponseEntity.ok(customerService.obtenerLoyaltyOwner(tenantId, customerId));
    }

    @Operation(summary = "Obtener historial de visitas/cortes del cliente para owner/admin")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{customerId}/history")
    public ResponseEntity<List<OwnerCustomerHistoryResponse>> history(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "10") int limit
    ) {
        adminPermissionService.checkPermission("CUSTOMERS_ACCESS");

        Long tenantId = extractTenantId(authHeader);
        return ResponseEntity.ok(customerService.obtenerHistorialOwner(tenantId, customerId, limit));
    }

    @Operation(summary = "Registrar cliente desde owner/admin")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    public ResponseEntity<ClienteResponse> create(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody VentaRapidaRequest request
    ) {
        adminPermissionService.checkPermission("CUSTOMERS_ACCESS");

        Long tenantId = extractTenantId(authHeader);
        Customer customer = customerService.registrarCliente(tenantId, request);

        boolean canViewPhone = adminPermissionService.hasCurrentUserPermission("CUSTOMERS_VIEW_PHONE");
        return ResponseEntity.ok(protectPhone(ClienteResponse.fromEntity(customer), canViewPhone));
    }

    @Operation(summary = "Actualizar cliente desde owner/admin")
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/{customerId}")
    public ResponseEntity<ClienteResponse> update(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long customerId,
            @RequestBody ActualizarClienteRequest request
    ) {
        adminPermissionService.checkPermission("CUSTOMERS_ACCESS");

        Long tenantId = extractTenantId(authHeader);
        Customer customer = customerService.actualizarCliente(tenantId, customerId, request);

        boolean canViewPhone = adminPermissionService.hasCurrentUserPermission("CUSTOMERS_VIEW_PHONE");
        return ResponseEntity.ok(protectPhone(ClienteResponse.fromEntity(customer), canViewPhone));
    }

    @PatchMapping("/{customerId}/whatsapp-consent")
    public ResponseEntity<ClienteResponse> updateWhatsappConsent(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long customerId,
            @RequestBody WhatsappConsentRequest request
    ) {
        adminPermissionService.checkPermission("CUSTOMERS_ACCESS");
        Long tenantId = extractTenantId(authHeader);
        Customer customer = customerService.actualizarConsentimientoWhatsapp(
                tenantId, customerId, request.getTransactionalEnabled(),
                request.getMarketingEnabled(), request.getOptedOut()
        );
        boolean canViewPhone = adminPermissionService.hasCurrentUserPermission("CUSTOMERS_VIEW_PHONE");
        return ResponseEntity.ok(protectPhone(ClienteResponse.fromEntity(customer), canViewPhone));
    }
    @Operation(summary = "Eliminar lógicamente cliente desde owner/admin")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{customerId}")
    public ResponseEntity<?> delete(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long customerId
    ) {
        adminPermissionService.checkPermission("CUSTOMERS_ACCESS");

        Long tenantId = extractTenantId(authHeader);
        customerService.eliminarClienteOwner(tenantId, customerId);

        return ResponseEntity.ok().body(java.util.Map.of(
                "success", true,
                "message", "Cliente eliminado correctamente"
        ));
    }




    @GetMapping("/export-count")
    public ResponseEntity<java.util.Map<String, Long>> exportCount(@RequestHeader("Authorization") String authHeader) {
        adminPermissionService.checkPermission("CUSTOMERS_ACCESS");
        return ResponseEntity.ok(java.util.Map.of("total", customerExportService.count(extractTenantId(authHeader))));
    }

    @GetMapping("/export.xlsx")
    public ResponseEntity<byte[]> exportExcel(@RequestHeader("Authorization") String authHeader) {
        adminPermissionService.checkPermission("CUSTOMERS_ACCESS");
        adminPermissionService.checkPermission("CUSTOMERS_VIEW_PHONE");
        String token = authHeader.replace("Bearer ", "");
        Long tenantId = jwtUtil.getTenantIdFromToken(token);
        Long userId = jwtUtil.getUserIdFromToken(token);
        byte[] file = customerExportService.exportXlsx(tenantId);
        long total = customerExportService.count(tenantId);
        generalAuditService.record(tenantId, null, userId, null, "CUSTOMER_EXPORT", null,
                "EXPORTED", "Exportacion completa de clientes", null,
                java.util.Map.of("format", "XLSX", "records", total));
        String filename = "clientes-" + java.time.LocalDate.now() + ".xlsx";
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .body(file);
    }

    private Long extractTenantId(String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        return jwtUtil.getTenantIdFromToken(token);
    }

    private ClienteResponse protectPhone(ClienteResponse response, boolean canViewPhone) {
        if (canViewPhone || response == null) {
            return response;
        }

        response.setPhone(maskPhone(response.getPhone()));
        return response;
    }

    private InactiveCustomerResponse protectPhone(InactiveCustomerResponse response, boolean canViewPhone) {
        if (canViewPhone || response == null) {
            return response;
        }

        return InactiveCustomerResponse.builder()
                .customerId(response.getCustomerId())
                .nombre(response.getNombre())
                .telefono(maskPhone(response.getTelefono()))
                .ultimaVisita(response.getUltimaVisita())
                .build();
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "";
        }

        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() <= 4) {
            return "****";
        }

        return "****" + digits.substring(digits.length() - 4);
    }


}

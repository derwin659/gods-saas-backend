package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.ActualizarClienteRequest;
import com.gods.saas.domain.dto.ClienteResponse;
import com.gods.saas.domain.dto.VentaRapidaRequest;
import com.gods.saas.domain.dto.response.InactiveCustomerResponse;
import com.gods.saas.domain.dto.response.OwnerCustomerHistoryResponse;
import com.gods.saas.domain.dto.response.OwnerCustomerLoyaltyResponse;
import com.gods.saas.domain.model.Customer;
import com.gods.saas.service.impl.AdminPermissionService;
import com.gods.saas.service.impl.CustomerService;
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

        List<ClienteResponse> response = customerService
                .listarClientesOwner(tenantId, q, limit)
                .stream()
                .map(customer -> ClienteResponse.fromEntity(
                        customer,
                        customerService.obtenerPuntosDisponiblesReales(tenantId, customer.getId())
                ))
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

        return ResponseEntity.ok(
                customerService.listarClientesInactivosOwner(tenantId, days)
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

        return ResponseEntity.ok(ClienteResponse.fromEntity(customer, puntosReales));
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

        return ResponseEntity.ok(ClienteResponse.fromEntity(customer));
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

        return ResponseEntity.ok(ClienteResponse.fromEntity(customer));
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




    private Long extractTenantId(String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        return jwtUtil.getTenantIdFromToken(token);
    }


}

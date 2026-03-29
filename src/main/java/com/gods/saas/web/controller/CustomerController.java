package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.*;
import com.gods.saas.domain.dto.response.ClientHomeResponse;
import com.gods.saas.domain.model.Customer;
import com.gods.saas.service.impl.AuthService;
import com.gods.saas.service.impl.CustomerService;
import com.gods.saas.service.impl.JwtService;
import com.gods.saas.utils.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;
    private final JwtUtil jwtUtil;
    private final JwtService jwtService;
    private final AuthService authService;

    @Operation(summary = "Registro rápido de clientes en caja")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/venta-rapida")
    public Customer crearClientePorVenta(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody VentaRapidaRequest request
    ) {
        String token = authHeader.replace("Bearer ", "");
        Long tenantId = jwtUtil.getTenantIdFromToken(token);

        return customerService.registrarCliente(tenantId, request);
    }

    @GetMapping("/me")
    public ResponseEntity<ClienteResponse> me(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");

        Long tenantId = jwtUtil.getTenantIdFromToken(token);
        Long customerId = jwtUtil.getCustomerIdFromToken(token);

        Customer c = customerService.obtenerClientePorId(tenantId, customerId);

        return ResponseEntity.ok(ClienteResponse.fromEntity(c));
    }

    @PutMapping("/clientes/{id}")
    public ResponseEntity<ClienteResponse> actualizarCliente(
            @PathVariable("id") Long clienteId,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody ActualizarClienteRequest request
    ) {
        String token = authHeader.replace("Bearer ", "");
        Long tenantId = jwtUtil.getTenantIdFromToken(token);

        Customer actualizado = customerService.actualizarCliente(tenantId, clienteId, request);

        ClienteResponse resp = ClienteResponse.fromEntity(actualizado);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/clientes/phone/{phone}")
    public ResponseEntity<ClienteResponse> getByPhone(
            @PathVariable String phone,
            @RequestHeader("Authorization") String authHeader
    ) {
        String token = authHeader.replace("Bearer ", "");
        Long tenantId = jwtUtil.getTenantIdFromToken(token);

        Customer user = customerService.obtenerClientePorTelefono(tenantId, phone);
        return ResponseEntity.ok(ClienteResponse.fromEntity(user));
    }

    @GetMapping("/clientes/{telefono}")
    public ResponseEntity<ClienteResponse> getByTelefono(
            @PathVariable String telefono,
            @RequestHeader("Authorization") String authHeader
    ) {
        String token = authHeader.replace("Bearer ", "");
        Long tenantId = jwtUtil.getTenantIdFromToken(token);

        Customer u = customerService.obtenerClientePorTelefono(tenantId, telefono);
        return ResponseEntity.ok(ClienteResponse.fromEntity(u));
    }

    @PutMapping("/clientes/{id}/telefono")
    public ResponseEntity<?> actualizarTelefono(
            @PathVariable Long id,
            @RequestBody ActualizarTelefonoRequest request) {

        authService.iniciarCambioTelefono(id, request.getNuevoTelefono());
        return ResponseEntity.ok("OTP enviado al nuevo número");
    }

    @PutMapping("/recuperar-cuenta/telefono")
    public ResponseEntity<?> recuperarCuenta(@RequestBody CambiarTelefonoRequest req) {
        Customer user = customerService.recuperarCuentaPorTelefono(req);

        RecuperarTelefonoResponse resp = new RecuperarTelefonoResponse(
                true,
                req.getOldPhone(),
                req.getNewPhone(),
                user.getTelefono(),
                "El número esta en proceso de cambio, terminamos de validar el OTP y cambiamos"
        );

        return ResponseEntity.ok(resp);
    }

    @GetMapping("/home")
    public ResponseEntity<ClientHomeResponse> home(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        Long tenantId = jwtUtil.getTenantIdFromToken(token);
        Long customerId = jwtUtil.getCustomerIdFromToken(token);

        return ResponseEntity.ok(customerService.getClientHome(tenantId, customerId));
    }
}

package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.*;
import com.gods.saas.service.impl.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/api/internal/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // =====================================================
    // LISTAR USUARIOS
    // =====================================================
    @GetMapping
    public List<AppUserResponse> getAll() {
        return userService.getUsers();
    }

    // =====================================================
    // OBTENER USUARIO POR ID
    // =====================================================
    @GetMapping("/{id}")
    public ResponseEntity<AppUserResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUser(id));
    }

    // =====================================================
    // CREAR USUARIO INTERNO
    // =====================================================
    @PostMapping
    public ResponseEntity<AppUserResponse> crear(@RequestBody CrearUsuarioRequest req) {
        return ResponseEntity.ok(userService.crearUsuario(req));
    }

    // =====================================================
    // ACTUALIZAR USUARIO
    // =====================================================
    @PutMapping("/{id}")
    public ResponseEntity<AppUserResponse> actualizar(
            @PathVariable Long id,
            @RequestBody ActualizarUsuarioInternoRequest req) {

        return ResponseEntity.ok(
                AppUserResponse.from(
                        userService.actualizarUsuario(
                                id,
                                req.getNombre(),
                                req.getApellido(),
                                req.getPhone(),
                                req.getBranchId(),
                                req.getRol()
                        )
                )
        );
    }

    // =====================================================
    // CAMBIAR PASSWORD
    // =====================================================
    @PutMapping("/{id}/password")
    public ResponseEntity<String> cambiarPassword(
            @PathVariable Long id,
            @RequestBody CambiarPasswordRequest req) {

        userService.cambiarPassword(
                id,
                userService.hashPassword(req.getNewPassword())
        );
        return ResponseEntity.ok("Password actualizado correctamente");
    }

    // =====================================================
    // CAMBIAR ESTADO
    // =====================================================
    @PutMapping("/{id}/estado")
    public ResponseEntity<String> cambiarEstado(
            @PathVariable Long id,
            @RequestBody CambiarEstadoRequest req) {

        userService.cambiarEstado(id, req.isActivo());
        return ResponseEntity.ok("Estado actualizado");
    }
}

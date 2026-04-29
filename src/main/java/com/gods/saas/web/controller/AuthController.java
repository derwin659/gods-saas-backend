package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.*;

import com.gods.saas.domain.dto.ForgotPasswordRequest;
import com.gods.saas.domain.dto.request.ResetPasswordRequest;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.AppUserRepository;
import com.gods.saas.domain.repository.UserTenantRoleRepository;
import com.gods.saas.service.impl.*;
import com.gods.saas.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final PasswordResetService passwordResetService;
    private final CustomerService customerService;
    private final AuthService authService;
    private final JwtService jwtService;
    private final UserTenantRoleService userTenantRoleService;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private UserTenantRoleRepository userTenantRoleRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/otp/send")
    public ResponseEntity<Void> sendOtp(@RequestBody SendOtpRequest request) {
        authService.sendOtp(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<?> verifyOtp(@RequestBody VerifyOtpRequest request) {
        Object response = authService.verifyOtp(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public MeResponse me(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        Long userId = jwtUtil.getUserIdFromToken(token);

        Customer u = customerService.getById(userId);

        MeResponse res = new MeResponse();
        res.setUserId(u.getId());
        res.setNombre(u.getNombres());
        res.setApellido(u.getApellidos());
        res.setPhone(u.getTelefono());
        res.setEmail(u.getEmail());
        res.setTenantId(u.getTenant().getId());

        return res;
    }

    @PostMapping("/login-final")
    public ResponseEntity<?> loginFinal(@RequestBody LoginFinalRequest req) {

        AppUser user = appUserRepository.findById(req.getUserId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Usuario no encontrado"
                ));

        String mode = req.getMode() == null ? "TENANT" : req.getMode().trim().toUpperCase();

        if ("SUPER_ADMIN".equals(mode)) {
            if (!"SUPER_ADMIN".equalsIgnoreCase(user.getRol())) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "El usuario no tiene acceso como SUPER_ADMIN"
                );
            }

            String token = jwtService.generateSuperAdminToken(user);

            return ResponseEntity.ok(
                    LoginFinalResponse.builder()
                            .token(token)
                            .userId(user.getId())
                            .nombre(user.getNombre())
                            .email(user.getEmail())
                            .role("SUPER_ADMIN")
                            .tenantId(null)
                            .tenantName(null)
                            .branchId(null)
                            .branchName(null)
                            .build()
            );
        }

        if (req.getTenantId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "tenantId es obligatorio para modo TENANT"
            );
        }

        UserTenantRole utr = userTenantRoleRepository
                .findByUserIdAndTenantIdWithRelations(req.getUserId(), req.getTenantId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "No tienes acceso a esta barbería"
                ));

        if (utr.getBranch() == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "El usuario no tiene sucursal asignada"
            );
        }

        Tenant tenant = utr.getTenant();
        Branch branch = utr.getBranch();

        String token = jwtService.generateToken(
                user,
                tenant.getId(),
                utr.getRole().name(),
                branch.getId()
        );

        return ResponseEntity.ok(
                LoginFinalResponse.builder()
                        .token(token)
                        .userId(user.getId())
                        .nombre(user.getNombre())
                        .email(user.getEmail())
                        .tenantId(tenant.getId())
                        .tenantName(tenant.getNombre())
                        .role(utr.getRole().name())
                        .branchId(branch.getId())
                        .branchName(branch.getNombre())
                        .build()
        );
    }

    @PostMapping("/login-basic")
    public ResponseEntity<?> loginBasic(@RequestBody LoginRequest req) {
        System.out.println("EMAIL REQ => " + req.getEmail());
        System.out.println("PASSWORD REQ => " + req.getPassword());
        var userOpt = appUserRepository.findByEmail(req.getEmail());
        if (userOpt.isPresent()) {
            var user = userOpt.get();
            System.out.println("PASSWORD REQ => [" + req.getPassword() + "]");
            System.out.println("PASSWORD LEN => " + req.getPassword().length());
            System.out.println("HASH DB DIRECT => [" + user.getPasswordHash() + "]");
            System.out.println("HASH LEN => " + user.getPasswordHash().length());
            System.out.println("DIRECT MATCH => " +
                    passwordEncoder.matches(req.getPassword(), user.getPasswordHash()));

        }
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            req.getEmail(),
                            req.getPassword()
                    )
            );

            AppUser user = (AppUser) auth.getPrincipal();

            List<UserTenantRole> roles = userTenantRoleService.getTenantsOfUser(user.getId());

            List<LoginResponse.TenantAccess> tenants = roles.stream()
                    .map(r -> new LoginResponse.TenantAccess(
                            r.getTenant().getId(),
                            r.getTenant().getNombre(),
                            r.getRole().name()
                    ))
                    .toList();

            return ResponseEntity.ok(
                    LoginResponse.builder()
                            .userId(user.getId())
                            .nombre(user.getNombre())
                            .globalRole(user.getRol())
                            .tenants(tenants)
                            .build()
            );

        } catch (BadCredentialsException e) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Usuario o contraseña inválidos"));
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        System.out.println("=======================================");
        System.out.println("LLEGO A /api/auth/forgot-password");
        System.out.println("EMAIL RECIBIDO => " + request.getEmail());

        passwordResetService.sendResetCode(request.getEmail());

        System.out.println("TERMINO passwordResetService.sendResetCode");
        System.out.println("=======================================");

        return ResponseEntity.ok(Map.of(
                "message", "Si el correo existe, enviaremos un código de recuperación."
        ));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        System.out.println("✅ LLEGÓ A /api/auth/reset-password");

        passwordResetService.resetPassword(request);

        System.out.println("✅ TERMINÓ resetPassword");

        return ResponseEntity.ok(Map.of(
                "message", "Contraseña actualizada correctamente."
        ));
    }


}
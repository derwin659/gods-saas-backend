package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.*;
import com.gods.saas.domain.model.AppUser;
import com.gods.saas.domain.model.Customer;
import com.gods.saas.domain.model.Tenant;
import com.gods.saas.domain.model.UserTenantRole;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;


import java.util.List;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final CustomerService customerService;

    private final AuthService authService;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private UserTenantRoleRepository userTenantRoleRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private final JwtService jwtService;
    private final UserTenantRoleService userTenantRoleService;

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

    // com.gods.saas.web.controller.AuthController

    @PostMapping("/me/perfil")
    public ResponseEntity<MeResponse> completarMiPerfil(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody PerfilClienteRequest request
    ) {
        String token = authHeader.replace("Bearer ", "");
        Long userId = jwtUtil.getUserIdFromToken(token);

        Customer userActualizado = customerService.completarPerfil(userId, request);

        MeResponse resp = new MeResponse();
        resp.setUserId(userActualizado.getId());
        resp.setNombre(userActualizado.getNombres());
        resp.setApellido(userActualizado.getApellidos());
        resp.setPhone(userActualizado.getTelefono());
        resp.setEmail(userActualizado.getEmail());
        resp.setTenantId(userActualizado.getTenant().getId());

        return ResponseEntity.ok(resp);
    }

    @PostMapping("/login-basic")
    public ResponseEntity<?> loginBasic(@RequestBody LoginRequest req) {
        System.out.println("paso aqui");
        System.out.println(req.getEmail());
        System.out.println(req.getPassword());
        try {

            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            req.getEmail(),
                            req.getPassword()
                    )
            );
            System.out.println("llego aqui {}");
            System.out.println(auth);
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
                    new LoginResponse(
                            user.getId(),
                            user.getNombre(),
                            tenants
                    )
            );

        } catch (BadCredentialsException e) {

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Usuario o contraseña inválidos"));

        }
    }

    @PostMapping("/login-final")
    public ResponseEntity<?> loginFinal(@RequestBody LoginFinalRequest req) {

        AppUser user = appUserRepository.findById(req.getUserId())
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Usuario no encontrado"
                        )
                );

        UserTenantRole utr = userTenantRoleRepository
                .findByUserIdAndTenantId(req.getUserId(), req.getTenantId())
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "No tienes acceso a esta barbería"
                        )
                );

        if (utr.getBranch() == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "El usuario no tiene sucursal asignada"
            );
        }

        String token = jwtService.generateToken(
                user,
                utr.getTenant().getId(),
                utr.getRole().name(),
                utr.getBranch().getId()
        );

        return ResponseEntity.ok(
                LoginFinalResponse.builder()
                        .token(token)
                        .userId(user.getId())
                        .nombre(user.getNombre())
                        .email(user.getEmail())
                        .tenantId(utr.getTenant().getId())
                        .tenantName(utr.getTenant().getNombre())
                        .role(utr.getRole().name())
                        .branchId(utr.getBranch().getId())
                        .branchName(utr.getBranch().getNombre())
                        .build()
        );
    }


}


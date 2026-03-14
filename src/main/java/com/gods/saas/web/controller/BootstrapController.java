package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.AppUserResponse;
import com.gods.saas.domain.dto.CrearUsuarioRequest;
import com.gods.saas.domain.dto.request.BootstrapOwnerRequest;
import com.gods.saas.service.impl.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bootstrap")
@RequiredArgsConstructor
public class BootstrapController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<AppUserResponse> register(@RequestBody BootstrapOwnerRequest req) {
        return ResponseEntity.ok(userService.bootstrapRegister(req));
    }
}

package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.*;
import com.gods.saas.domain.model.AppUser;
import com.gods.saas.domain.model.Tenant;
import com.gods.saas.domain.model.UserTenantRole;
import com.gods.saas.service.impl.FileStorageServiceImpl;
import com.gods.saas.service.impl.TenantUserService;
import com.gods.saas.service.impl.UserService;
import com.gods.saas.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/api/tenant/users")
@RequiredArgsConstructor
public class TenantUserController {

    private final TenantUserService service;
    private final UserService userService;
    private final JwtUtil jwtUtil;

    @Autowired
    FileStorageServiceImpl  fileStorageService;

    private Long tenantId(String auth) {
        return jwtUtil.getTenantIdFromToken(auth.replace("Bearer ", ""));
    }


    // -----------------------------------------
    // 4. OBTENER POR ID
    // -----------------------------------------

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public TenantUserDto getById(@PathVariable Long id) {
        return service.getById(id);
    }


    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public List<TenantUserResponse> list(@RequestHeader("Authorization") String auth) {
        return service.listUsers(tenantId(auth));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public void create(
            @RequestHeader("Authorization") String auth,

            @RequestPart("data") CreateUserRequest req,
            @RequestPart(value = "photo", required = false) MultipartFile photo
    ) {
        String photoUrl = null;

        if (photo != null && !photo.isEmpty()) {
            photoUrl = fileStorageService.uploadUserPhoto(photo);
        }

        service.createUser(tenantId(auth), req, photoUrl);
    }




    @PutMapping("/{userId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public void update(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long userId,
            @RequestPart("data") UpdateUserRequest req,
            @RequestPart(value = "photo", required = false
    ) MultipartFile photo ) {

        String photoUrl = null;

        if (photo != null && !photo.isEmpty()) {
            photoUrl = fileStorageService.uploadUserPhoto(photo);
        }

        service.updateUser(tenantId(auth), userId, req, photoUrl);


    }

    @PutMapping("/{userId}/status")
    @PreAuthorize("hasRole('OWNER')")
    public void toggle(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long userId
    ) {
        service.toggleUserStatus(tenantId(auth), userId);
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('OWNER')")
    public void delete(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long userId
    ) {
        service.deleteUser(tenantId(auth), userId);
    }
}


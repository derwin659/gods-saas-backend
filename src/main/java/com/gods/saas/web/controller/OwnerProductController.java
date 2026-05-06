package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.AdjustProductStockRequest;
import com.gods.saas.domain.dto.request.SaveProductRequest;
import com.gods.saas.domain.dto.response.ProductResponse;
import com.gods.saas.service.impl.AdminPermissionService;
import com.gods.saas.service.impl.impl.OwnerProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/owner/products")
@RequiredArgsConstructor
public class OwnerProductController {

    private final OwnerProductService ownerProductService;
    private final AdminPermissionService adminPermissionService;

    @GetMapping
    public List<ProductResponse> findAll(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Boolean activeOnly
    ) {
        adminPermissionService.checkPermission("CONFIG_PRODUCTS");

        Long effectiveBranchId = branchId != null ? branchId : sessionBranchId;
        return ownerProductService.findAll(tenantId, effectiveBranchId, activeOnly);
    }

    @GetMapping("/{productId}")
    public ProductResponse findById(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @PathVariable Long productId,
            @RequestParam(required = false) Long branchId
    ) {
        adminPermissionService.checkPermission("CONFIG_PRODUCTS");

        Long effectiveBranchId = branchId != null ? branchId : sessionBranchId;
        return ownerProductService.findById(tenantId, effectiveBranchId, productId);
    }

    @PostMapping
    public ProductResponse create(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @RequestAttribute("userId") Long userId,
            @RequestParam(required = false) Long branchId,
            @RequestBody SaveProductRequest request
    ) {
        adminPermissionService.checkPermission("CONFIG_PRODUCTS");

        Long effectiveBranchId = branchId != null ? branchId : sessionBranchId;
        return ownerProductService.create(tenantId, effectiveBranchId, userId, request);
    }

    @PutMapping("/{productId}")
    public ProductResponse update(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @RequestAttribute("userId") Long userId,
            @PathVariable Long productId,
            @RequestParam(required = false) Long branchId,
            @RequestBody SaveProductRequest request
    ) {
        adminPermissionService.checkPermission("CONFIG_PRODUCTS");

        Long effectiveBranchId = branchId != null ? branchId : sessionBranchId;
        return ownerProductService.update(tenantId, effectiveBranchId, userId, productId, request);
    }

    @PatchMapping("/{productId}/toggle-active")
    public ProductResponse toggleActive(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @RequestAttribute("userId") Long userId,
            @PathVariable Long productId,
            @RequestParam(required = false) Long branchId
    ) {
        adminPermissionService.checkPermission("CONFIG_PRODUCTS");

        Long effectiveBranchId = branchId != null ? branchId : sessionBranchId;
        return ownerProductService.toggleActive(tenantId, effectiveBranchId, userId, productId);
    }

    @PostMapping("/{productId}/image")
    public ProductResponse uploadImage(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @RequestAttribute("userId") Long userId,
            @PathVariable Long productId,
            @RequestParam(required = false) Long branchId,
            @RequestParam("file") MultipartFile file
    ) {
        adminPermissionService.checkPermission("CONFIG_PRODUCTS");

        Long effectiveBranchId = branchId != null ? branchId : sessionBranchId;
        return ownerProductService.uploadImage(tenantId, effectiveBranchId, userId, productId, file);
    }

    @PatchMapping("/{productId}/stock")
    public ProductResponse adjustStock(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @RequestAttribute("userId") Long userId,
            @PathVariable Long productId,
            @RequestParam(required = false) Long branchId,
            @RequestBody AdjustProductStockRequest request
    ) {
        adminPermissionService.checkPermission("CONFIG_PRODUCTS");

        Long effectiveBranchId = branchId != null ? branchId : sessionBranchId;
        return ownerProductService.adjustStock(tenantId, effectiveBranchId, userId, productId, request);
    }
}
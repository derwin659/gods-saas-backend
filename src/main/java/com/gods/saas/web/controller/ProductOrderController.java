package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.CreatePublicProductOrderRequest;
import com.gods.saas.domain.dto.request.UpdateProductOrderStatusRequest;
import com.gods.saas.domain.dto.response.ProductOrderResponse;
import com.gods.saas.domain.dto.response.ProductResponse;
import com.gods.saas.service.impl.AdminPermissionService;
import com.gods.saas.service.impl.ProductOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProductOrderController {

    private final ProductOrderService productOrderService;
    private final AdminPermissionService adminPermissionService;

    @GetMapping("/api/public/booking/{codigoNegocio}/products")
    public List<ProductResponse> publicProducts(
            @PathVariable String codigoNegocio,
            @RequestParam(required = false) Long branchId
    ) {
        return productOrderService.publicProducts(codigoNegocio, branchId);
    }

    @PostMapping("/api/public/booking/{codigoNegocio}/product-orders")
    public ProductOrderResponse createPublicOrder(
            @PathVariable String codigoNegocio,
            @RequestBody CreatePublicProductOrderRequest request
    ) {
        return productOrderService.createPublicOrder(codigoNegocio, request);
    }

    @GetMapping("/api/owner/product-orders")
    public List<ProductOrderResponse> ownerOrders(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) String status
    ) {
        adminPermissionService.checkPermission("CONFIG_PRODUCTS");
        Long effectiveBranchId = branchId != null ? branchId : sessionBranchId;
        return productOrderService.ownerOrders(tenantId, effectiveBranchId, status);
    }

    @PostMapping("/api/owner/product-orders/{orderId}/approve")
    public ProductOrderResponse approve(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @PathVariable Long orderId,
            @RequestParam(required = false) Long branchId,
            @RequestBody(required = false) UpdateProductOrderStatusRequest request
    ) {
        adminPermissionService.checkPermission("CONFIG_PRODUCTS");
        Long effectiveBranchId = branchId != null ? branchId : sessionBranchId;
        return productOrderService.approve(tenantId, effectiveBranchId, orderId, request != null ? request.getNote() : null);
    }

    @PostMapping("/api/owner/product-orders/{orderId}/reject")
    public ProductOrderResponse reject(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @PathVariable Long orderId,
            @RequestParam(required = false) Long branchId,
            @RequestBody(required = false) UpdateProductOrderStatusRequest request
    ) {
        adminPermissionService.checkPermission("CONFIG_PRODUCTS");
        Long effectiveBranchId = branchId != null ? branchId : sessionBranchId;
        return productOrderService.reject(tenantId, effectiveBranchId, orderId, request != null ? request.getNote() : null);
    }

    @PostMapping("/api/owner/product-orders/{orderId}/cancel")
    public ProductOrderResponse cancel(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @PathVariable Long orderId,
            @RequestParam(required = false) Long branchId,
            @RequestBody(required = false) UpdateProductOrderStatusRequest request
    ) {
        adminPermissionService.checkPermission("CONFIG_PRODUCTS");
        Long effectiveBranchId = branchId != null ? branchId : sessionBranchId;
        return productOrderService.cancel(tenantId, effectiveBranchId, orderId, request != null ? request.getNote() : null);
    }

    @PostMapping("/api/owner/product-orders/{orderId}/deliver")
    public ProductOrderResponse deliver(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @RequestAttribute("userId") Long userId,
            @PathVariable Long orderId,
            @RequestParam(required = false) Long branchId
    ) {
        adminPermissionService.checkPermission("CONFIG_PRODUCTS");
        Long effectiveBranchId = branchId != null ? branchId : sessionBranchId;
        return productOrderService.deliver(tenantId, effectiveBranchId, userId, orderId);
    }
}

package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.PromotionRequest;
import com.gods.saas.domain.dto.response.PromotionResponse;
import com.gods.saas.service.impl.impl.PromotionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/owner/promotions")
public class OwnerPromotionController {

    private final PromotionService promotionService;

    @GetMapping
    public List<PromotionResponse> list(Authentication authentication) {
        Long tenantId = extractTenantId(authentication);
        return promotionService.getOwnerPromotions(tenantId);
    }

    @GetMapping("/{id}")
    public PromotionResponse getById(@PathVariable Long id, Authentication authentication) {
        Long tenantId = extractTenantId(authentication);
        return promotionService.getOwnerPromotionById(tenantId, id);
    }

    @PostMapping
    public PromotionResponse create(@RequestBody PromotionRequest request, Authentication authentication) {
        Long tenantId = extractTenantId(authentication);
        return promotionService.createPromotion(tenantId, request);
    }

    @PutMapping("/{id}")
    public PromotionResponse update(
            @PathVariable Long id,
            @RequestBody PromotionRequest request,
            Authentication authentication
    ) {
        Long tenantId = extractTenantId(authentication);
        return promotionService.updatePromotion(tenantId, id, request);
    }



    @PostMapping(value = "/{id}/image", consumes = "multipart/form-data")
    public PromotionResponse uploadImage(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file,
            Authentication authentication
    ) {
        Long tenantId = extractTenantId(authentication);
        return promotionService.uploadPromotionImage(tenantId, id, file);
    }

    @PatchMapping("/{id}/toggle")
    public PromotionResponse toggle(@PathVariable Long id, Authentication authentication) {
        Long tenantId = extractTenantId(authentication);
        return promotionService.togglePromotion(tenantId, id);
    }

    @DeleteMapping("/{id}")
    public Map<String, String> delete(@PathVariable Long id, Authentication authentication) {
        Long tenantId = extractTenantId(authentication);
        promotionService.deletePromotion(tenantId, id);
        return Map.of("message", "Promoción eliminada correctamente");
    }

    private Long extractTenantId(Authentication authentication) {
        Object details = authentication.getDetails();
        if (details instanceof Map<?, ?> detailsMap) {
            Object tenantId = detailsMap.get("tenantId");
            if (tenantId instanceof Number n) return n.longValue();
            if (tenantId instanceof String s) return Long.parseLong(s);
        }

        throw new IllegalStateException("No se pudo obtener el tenantId del token");
    }
}

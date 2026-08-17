package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.response.ShowcaseResponse;
import com.gods.saas.service.impl.ProfessionalShowcaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/public/showcase")
@RequiredArgsConstructor
public class PublicShowcaseController {
    private final ProfessionalShowcaseService service;

    @GetMapping
    public Object list(
            @RequestParam Long tenantId,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) List<Long> branchIds,
            @RequestParam(required = false) Long professionalUserId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        if (page == null && size == null) {
            if (branchId == null) {
                throw new IllegalArgumentException("Selecciona una sede.");
            }
            return service.published(tenantId, branchId, professionalUserId);
        }
        List<Long> selected = new ArrayList<>();
        if (branchIds != null) selected.addAll(branchIds);
        if (branchId != null) selected.add(branchId);
        return service.publishedPage(
                tenantId,
                selected,
                professionalUserId,
                page == null ? 0 : page,
                size == null ? 12 : size
        );
    }
}
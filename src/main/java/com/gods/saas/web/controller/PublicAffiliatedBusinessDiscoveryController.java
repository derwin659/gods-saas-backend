package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.response.PublicAffiliatedBranchResponse;
import com.gods.saas.domain.dto.response.PublicAffiliatedBranchDetailResponse;
import com.gods.saas.service.impl.PublicAffiliatedBusinessDiscoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public/affiliated-branches")
@RequiredArgsConstructor
public class PublicAffiliatedBusinessDiscoveryController {

    private final PublicAffiliatedBusinessDiscoveryService discoveryService;

    @GetMapping("/{branchId}")
    public PublicAffiliatedBranchDetailResponse detail(@PathVariable Long branchId) {
        return discoveryService.detail(branchId);
    }

    @GetMapping
    public List<PublicAffiliatedBranchResponse> search(
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Double radiusKm,
            @RequestParam(required = false) Integer limit
    ) {
        return discoveryService.search(latitude, longitude, city, businessType, q, radiusKm, limit);
    }
}
package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.RewardItemRequest;
import com.gods.saas.domain.dto.response.RewardItemResponse;
import com.gods.saas.service.impl.impl.RewardItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/owner/rewards")
@RequiredArgsConstructor
public class RewardItemController {

    private final RewardItemService service;

    @GetMapping
    public List<RewardItemResponse> getAll(
            @RequestParam(required = false) Boolean onlyActive,
            Authentication authentication
    ) {
        Map<String, Object> details = (Map<String, Object>) authentication.getDetails();
        Long tenantId = Long.valueOf(details.get("tenantId").toString());
        return service.getAll(tenantId, onlyActive);
    }

    @PostMapping
    public RewardItemResponse create(
            @RequestBody RewardItemRequest request,
            Authentication authentication
    ) {
        Map<String, Object> details = (Map<String, Object>) authentication.getDetails();
        Long tenantId = Long.valueOf(details.get("tenantId").toString());
        return service.create(tenantId, request);
    }

    @PutMapping("/{id}")
    public RewardItemResponse update(
            @PathVariable Long id,
            @RequestBody RewardItemRequest request,
            Authentication authentication
    ) {
        Map<String, Object> details = (Map<String, Object>) authentication.getDetails();
        Long tenantId = Long.valueOf(details.get("tenantId").toString());
        return service.update(tenantId, id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(
            @PathVariable Long id,
            Authentication authentication
    ) {
        Map<String, Object> details = (Map<String, Object>) authentication.getDetails();
        Long tenantId = Long.valueOf(details.get("tenantId").toString());
        service.delete(tenantId, id);
    }
}
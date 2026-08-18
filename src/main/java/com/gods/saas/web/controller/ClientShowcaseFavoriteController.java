package com.gods.saas.web.controller;

import com.gods.saas.service.impl.ShowcaseFavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/client/showcase-favorites")
@RequiredArgsConstructor
public class ClientShowcaseFavoriteController {
    private final ShowcaseFavoriteService service;

    @GetMapping
    public List<Long> list(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("userId") Long userId
    ) {
        return service.list(tenantId, userId);
    }

    @PutMapping("/{showcaseId}")
    public void add(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("userId") Long userId,
            @PathVariable Long showcaseId
    ) {
        service.add(tenantId, userId, showcaseId);
    }

    @DeleteMapping("/{showcaseId}")
    public void remove(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("userId") Long userId,
            @PathVariable Long showcaseId
    ) {
        service.remove(tenantId, userId, showcaseId);
    }
}
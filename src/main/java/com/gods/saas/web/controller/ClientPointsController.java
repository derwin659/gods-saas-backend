package com.gods.saas.web.controller;


import com.gods.saas.domain.dto.response.ClientPointsResponse;
import com.gods.saas.service.impl.ClientPointsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clients/points")
@RequiredArgsConstructor
public class ClientPointsController {

    private final ClientPointsServiceImpl clientPointsService;

    @GetMapping
    public ResponseEntity<ClientPointsResponse> getClientPoints(Authentication authentication) {
        return ResponseEntity.ok(clientPointsService.getClientPoints(authentication));
    }
}

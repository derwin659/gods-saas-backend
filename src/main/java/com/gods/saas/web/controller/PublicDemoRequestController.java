package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.CreateDemoRequest;
import com.gods.saas.domain.dto.response.DemoRequestResponse;
import com.gods.saas.service.impl.impl.DemoRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CrossOrigin
@RestController
@RequestMapping("/api/public/demo-requests")
@RequiredArgsConstructor
public class PublicDemoRequestController {

    private final DemoRequestService demoRequestService;

    @PostMapping
    public ResponseEntity<DemoRequestResponse> create(
            @RequestBody CreateDemoRequest request
    ) {
        return ResponseEntity.ok(demoRequestService.createPublicRequest(request));
    }
}
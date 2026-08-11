package com.gods.saas.web.controller;

import com.gods.saas.service.impl.QzSigningService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/owner/qz-signing")
@RequiredArgsConstructor
public class QzSigningController {
    private final QzSigningService qzSigningService;

    @GetMapping(value = "/certificate", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> certificate() {
        return ResponseEntity.ok(qzSigningService.certificate());
    }

    @PostMapping(value = "/sign", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> sign(@RequestBody String payload) {
        return ResponseEntity.ok(qzSigningService.sign(payload));
    }
}
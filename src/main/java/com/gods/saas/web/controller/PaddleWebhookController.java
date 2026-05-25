package com.gods.saas.web.controller;

import com.gods.saas.service.impl.PaddleWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks/paddle")
@RequiredArgsConstructor
public class PaddleWebhookController {

    private final PaddleWebhookService paddleWebhookService;

    @PostMapping
    public ResponseEntity<String> handlePaddleWebhook(
            @RequestBody String rawBody,
            @RequestHeader(name = "Paddle-Signature", required = false) String signature
    ) {
        paddleWebhookService.handleWebhook(rawBody, signature);
        return ResponseEntity.ok("ok");
    }
}

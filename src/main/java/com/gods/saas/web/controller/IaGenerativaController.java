package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.GenerarPreviewRequest;
import com.gods.saas.domain.dto.response.GenerarPreviewResponse;
import com.gods.saas.service.impl.IaGenerativaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ia-generativa")
@RequiredArgsConstructor
public class IaGenerativaController {

    private final IaGenerativaService iaGenerativaService;

    @PostMapping("/generar-preview")
    public ResponseEntity<GenerarPreviewResponse> generarPreview(
            @RequestBody GenerarPreviewRequest request
    ) {
        return ResponseEntity.ok(
                iaGenerativaService.generarPreview(request)
        );
    }
}


package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.GenerarImagenRequest;
import com.gods.saas.domain.dto.request.GenerarPreviewRequest;
import com.gods.saas.domain.dto.response.GenerarImagenResponse;
import com.gods.saas.domain.dto.response.GenerarPreviewResponse;
import com.gods.saas.service.impl.IaGenerativaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/generar")
    public ResponseEntity<GenerarImagenResponse> generar(
            @RequestBody GenerarImagenRequest request
    ) {
        return ResponseEntity.ok(
                iaGenerativaService.generarImagenReal(request)
        );
    }
}
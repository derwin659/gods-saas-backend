package com.gods.saas.web.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.gods.saas.domain.dto.request.AnalizarImagenRequest;
import com.gods.saas.domain.dto.response.UxAnalisisResponse;
import com.gods.saas.service.impl.SesionIAService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ia/analitica")
@RequiredArgsConstructor
public class IaAnaliticaController {

    private final SesionIAService iaAnaliticaService;

    @PostMapping("/{sesionId}/analizar")
    @PreAuthorize("hasAnyRole('ADMIN','BARBER','OWNER')")
    public ResponseEntity<UxAnalisisResponse> analizarImagen(
            @PathVariable String sesionId,
            @RequestBody AnalizarImagenRequest analizarImagenRequest
    ) throws JsonProcessingException {
        String imagenBase64 = analizarImagenRequest.getImagenBase64();
        UxAnalisisResponse uxAnalisisResponse = iaAnaliticaService.ejecutarAnalitica(sesionId, imagenBase64);

        return  ResponseEntity.ok(uxAnalisisResponse);

    }

}


package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.GenerarPreviewRequest;
import com.gods.saas.domain.dto.response.GenerarPreviewResponse;
import com.gods.saas.domain.dto.response.ImagenesPreview;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IaGenerativaService {

    public GenerarPreviewResponse generarPreview(
            GenerarPreviewRequest request
    ) {

        boolean densidadAlta =
                "alta".equalsIgnoreCase(
                        request.getContexto().getDensidadCabello()
                );

        if (request.getOpciones().getOndulado().isAplicar()
                && !densidadAlta) {

            return GenerarPreviewResponse.builder()
                    .estado("BLOQUEADO")
                    .mensaje("El ondulado no está disponible para tu tipo de cabello")
                    .build();
        }

        // 🔥 MOCK (simulación de IA generativa)
        return GenerarPreviewResponse.builder()
                .estado("OK")
                .mensaje("Vista previa generada correctamente")
                .imagenes(
                        ImagenesPreview.builder()
                                .frontal("https://cdn.gods.ai/previews/frontal.png")
                                .lateral("https://cdn.gods.ai/previews/lateral.png")
                                .trasera("https://cdn.gods.ai/previews/trasera.png")
                                .build()
                )
                .build();
    }
}


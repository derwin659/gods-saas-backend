package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.GenerarImagenRequest;
import com.gods.saas.domain.dto.request.Imagenes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiIllustrativeAssetService {

    private final CloudinaryStorageService storage;

    public PreparedRequest prepare(Long tenantId, GenerarImagenRequest source) {
        if (tenantId == null) {
            throw new IllegalStateException("El tenant es obligatorio para transportar imágenes en modo SERVERLESS");
        }
        if (source == null || source.getImagenes() == null) {
            throw new IllegalArgumentException("Las imágenes son obligatorias");
        }

        List<String> publicIds = new ArrayList<>();
        try {
            Imagenes urls = Imagenes.builder()
                    .frontal(upload(tenantId, source.getSesionId(), "frontal", source.getImagenes().getFrontal(), publicIds))
                    .lateral(upload(tenantId, source.getSesionId(), "lateral", source.getImagenes().getLateral(), publicIds))
                    .trasera(upload(tenantId, source.getSesionId(), "trasera", source.getImagenes().getTrasera(), publicIds))
                    .build();

            GenerarImagenRequest request = GenerarImagenRequest.builder()
                    .sesionId(source.getSesionId())
                    .imagenes(urls)
                    .corte(source.getCorte())
                    .tinte(source.getTinte())
                    .ondulado(source.getOndulado())
                    .vistas(source.getVistas())
                    .build();
            return new PreparedRequest(request, List.copyOf(publicIds));
        } catch (RuntimeException error) {
            cleanup(publicIds);
            throw error;
        }
    }

    public void cleanup(PreparedRequest prepared) {
        if (prepared != null) cleanup(prepared.publicIds());
    }

    private String upload(Long tenantId, String sessionId, String view, String value, List<String> publicIds) {
        if (value == null || value.isBlank()) return null;
        byte[] bytes = decodeBase64(value);
        CloudinaryStorageService.UploadResult result = storage.uploadAiInputImage(tenantId, sessionId, view, bytes);
        publicIds.add(result.getPublicId());
        return result.getSecureUrl();
    }

    private byte[] decodeBase64(String value) {
        String payload = value.trim();
        int comma = payload.indexOf(',');
        if (payload.startsWith("data:") && comma >= 0) payload = payload.substring(comma + 1);
        try {
            return Base64.getDecoder().decode(payload);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Una imagen para la IA no contiene Base64 válido", error);
        }
    }

    private void cleanup(List<String> publicIds) {
        for (String publicId : publicIds) {
            try {
                storage.deleteAiInputImage(publicId);
            } catch (RuntimeException cleanupError) {
                log.warn("No se pudo limpiar el recurso temporal de IA {}", publicId, cleanupError);
            }
        }
    }

    public record PreparedRequest(GenerarImagenRequest request, List<String> publicIds) {
    }
}
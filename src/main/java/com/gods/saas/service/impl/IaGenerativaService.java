package com.gods.saas.service.impl;

import com.gods.saas.client.IaIlustrativaClient;
import com.gods.saas.domain.dto.request.*;
import com.gods.saas.domain.dto.response.GenerarImagenResponse;
import com.gods.saas.domain.dto.response.GenerarPreviewResponse;
import com.gods.saas.domain.dto.response.ImagenesPreview;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IaGenerativaService {

    private final IaIlustrativaClient iaIlustrativaClient;

    public GenerarPreviewResponse generarPreview(GenerarPreviewRequest request) {

        if (request == null) {
            return GenerarPreviewResponse.builder()
                    .estado("ERROR")
                    .mensaje("La solicitud es inválida")
                    .build();
        }

        if (isBlank(request.getImagenFrontalBase64())
                || isBlank(request.getImagenLateralBase64())
                || isBlank(request.getImagenTraseraBase64())) {
            return GenerarPreviewResponse.builder()
                    .estado("ERROR")
                    .mensaje("Debes enviar las 3 imágenes: frontal, lateral y trasera")
                    .build();
        }

        if (request.getCorte() == null || isBlank(request.getCorte().getNombre())) {
            return GenerarPreviewResponse.builder()
                    .estado("ERROR")
                    .mensaje("Debes seleccionar un corte")
                    .build();
        }

        boolean densidadAlta = request.getContexto() != null
                && "alta".equalsIgnoreCase(safe(request.getContexto().getDensidadCabello()));

        OnduladoRequest ondulado = request.getOpciones() != null
                ? request.getOpciones().getOndulado()
                : null;

        TinteRequest tinte = request.getOpciones() != null
                ? request.getOpciones().getTinte()
                : null;

        boolean aplicarOndulado = ondulado != null && isTrue(ondulado.getAplicar());
        boolean aplicarTinte = tinte != null && isTrue(tinte.getAplicar());

        String tipoOndulado = ondulado != null ? trimToNull(ondulado.getTipo()) : null;
        String colorTinte = tinte != null ? trimToNull(tinte.getColor()) : null;

        if (aplicarOndulado && tipoOndulado == null) {
            return GenerarPreviewResponse.builder()
                    .estado("ERROR")
                    .mensaje("Debes indicar el tipo de ondulado")
                    .build();
        }

        if (aplicarTinte && colorTinte == null) {
            return GenerarPreviewResponse.builder()
                    .estado("ERROR")
                    .mensaje("Debes indicar el color del tinte")
                    .build();
        }

        if (aplicarOndulado && !densidadAlta) {
            return GenerarPreviewResponse.builder()
                    .estado("BLOQUEADO")
                    .mensaje("El ondulado no está disponible para tu tipo de cabello")
                    .build();
        }

        try {
            String corte = request.getCorte().getNombre().trim();

            GenerarImagenRequest generarImagenRequest = mapToGenerarImagenRequest(
                    request,
                    aplicarOndulado,
                    tipoOndulado,
                    aplicarTinte,
                    colorTinte
            );

            GenerarImagenResponse response = iaIlustrativaClient.generarImagen(generarImagenRequest);

            if (response == null || response.getImagenes() == null) {
                return GenerarPreviewResponse.builder()
                        .estado("ERROR")
                        .mensaje("La IA ilustrativa no devolvió imágenes")
                        .build();
            }

            return GenerarPreviewResponse.builder()
                    .estado("OK")
                    .mensaje(buildMensaje(corte, aplicarOndulado, tipoOndulado, aplicarTinte, colorTinte))
                    .imagenes(
                            ImagenesPreview.builder()
                                    .frontal(response.getImagenes().getFrontal())
                                    .lateral(response.getImagenes().getLateral())
                                    .trasera(response.getImagenes().getTrasera())
                                    .build()
                    )
                    .build();

        } catch (Exception e) {
            return GenerarPreviewResponse.builder()
                    .estado("ERROR")
                    .mensaje("No se pudo generar la vista previa: " + e.getMessage())
                    .build();
        }
    }

    public GenerarImagenResponse generarImagenReal(GenerarImagenRequest request) {
        validarGenerarImagenRequest(request);
        return iaIlustrativaClient.generarImagen(request);
    }

    private GenerarImagenRequest mapToGenerarImagenRequest(
            GenerarPreviewRequest request,
            boolean aplicarOndulado,
            String tipoOndulado,
            boolean aplicarTinte,
            String colorTinte
    ) {
        Imagenes imagenes = new Imagenes();
        imagenes.setFrontal(cleanBase64(request.getImagenFrontalBase64()));
        imagenes.setLateral(cleanBase64(request.getImagenLateralBase64()));
        imagenes.setTrasera(cleanBase64(request.getImagenTraseraBase64()));

        CorteDTO corteDTO = new CorteDTO();
        corteDTO.setNombre(request.getCorte().getNombre().trim());

        OnduladoDTO onduladoDTO = null;
        if (aplicarOndulado) {
            onduladoDTO = new OnduladoDTO();
            onduladoDTO.setAplicar(true);
            onduladoDTO.setTipo(tipoOndulado);
        }

        TinteDTO tinteDTO = null;
        if (aplicarTinte) {
            tinteDTO = new TinteDTO();
            tinteDTO.setAplicar(true);
            tinteDTO.setColor(colorTinte);
        }

        return GenerarImagenRequest.builder()
                .sesionId("SES-" + UUID.randomUUID())
                .imagenes(imagenes)
                .corte(corteDTO)
                .ondulado(onduladoDTO)
                .tinte(tinteDTO)
                .vistas(List.of("frontal", "lateral", "trasera"))
                .build();
    }

    private void validarGenerarImagenRequest(GenerarImagenRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("La solicitud es inválida");
        }

        if (request.getImagenes() == null
                || isBlank(request.getImagenes().getFrontal())
                || isBlank(request.getImagenes().getLateral())
                || isBlank(request.getImagenes().getTrasera())) {
            throw new IllegalArgumentException("Debes enviar frontal, lateral y trasera");
        }

        if (request.getCorte() == null || isBlank(request.getCorte().getNombre())) {
            throw new IllegalArgumentException("Debes indicar el corte");
        }
    }

    private String buildMensaje(
            String corte,
            boolean aplicarOndulado,
            String tipoOndulado,
            boolean aplicarTinte,
            String colorTinte
    ) {
        StringBuilder sb = new StringBuilder("Vista previa generada correctamente");
        sb.append(" para el corte ").append(corte);

        if (aplicarOndulado) {
            sb.append(", con ondulado");
            if (tipoOndulado != null) {
                sb.append(" tipo ").append(tipoOndulado);
            }
        }

        if (aplicarTinte) {
            sb.append(", con tinte");
            if (colorTinte != null) {
                sb.append(" color ").append(colorTinte);
            }
        }

        return sb.toString();
    }

    private String cleanBase64(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceFirst("^data:image/[^;]+;base64,", "").trim();
    }

    private boolean isTrue(Boolean value) {
        return Boolean.TRUE.equals(value);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}